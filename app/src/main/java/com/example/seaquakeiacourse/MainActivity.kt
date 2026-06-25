package com.example.seaquakeiacourse

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.core.DataStore
import coil.compose.AsyncImage
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.example.seaquakeiacourse.ui.theme.SeaquakeIACourseTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import java.util.*

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_memory")

class MainActivity : ComponentActivity() {
    private var voiceManager: VoiceManager? = null
    private val geminiService by lazy { GeminiService(BuildConfig.GEMINI_API_KEY) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setupRemoteConfig()

        // Esconde as barras de sistema (status e navegação) globalmente para todo o app
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        voiceManager = VoiceManager(this)
        setContent {
            SeaquakeIACourseTheme {
                CompositionLocalProvider(
                    LocalVoiceManager provides voiceManager!!,
                    LocalGeminiService provides geminiService
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun setupRemoteConfig() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val latestVersion = remoteConfig.getLong("versao_mais_recente")
                if (latestVersion > BuildConfig.VERSION_CODE) {
                    showUpdateDialog()
                }
            }
        }
    }

    private fun showUpdateDialog() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://alpduarte.github.io/Seaquake-Language-Course/app-release.apk"))
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Atualização Disponível")
            .setMessage("Uma nova versão do Seaquake IA Course está disponível. Deseja baixar agora?")
            .setPositiveButton("Sim") { _, _ ->
                startActivity(intent)
            }
            .setNegativeButton("Mais tarde", null)
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        voiceManager?.stop()
        super.onDestroy()
    }
}

val LocalVoiceManager = staticCompositionLocalOf<VoiceManager> { error("No VoiceManager provided") }
val LocalGeminiService = staticCompositionLocalOf<GeminiService> { error("No GeminiService provided") }

class VoiceManager(context: Context) {
    private var tts: TextToSpeech? = null
    var isReady = mutableStateOf(false)
    var isSpeaking = mutableStateOf(false)
    private var speakingStartTime = 0L
    var onSpeakingFinished: ((Long) -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady.value = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking.value = true
                        speakingStartTime = System.currentTimeMillis()
                    }
                    override fun onDone(utteranceId: String?) {
                        isSpeaking.value = false
                        if (speakingStartTime > 0) {
                            val duration = System.currentTimeMillis() - speakingStartTime
                            onSpeakingFinished?.invoke(duration)
                            speakingStartTime = 0
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        isSpeaking.value = false
                        speakingStartTime = 0
                    }
                })
            }
        }
    }

    fun speak(text: String, locale: Locale = Locale("br", "BR"), gender: String = "F") {
        if (isReady.value) {
            // Normaliza o locale para garantir reconhecimento
            val targetLocale = if (locale.language.lowercase() == "br") Locale("pt", "BR") else locale
            tts?.language = targetLocale

            val voices = tts?.voices
            if (voices != null) {
                // Tenta encontrar uma voz que corresponda ao idioma, país e gênero
                val preferredVoice = voices.filter { voice ->
                    val vLocale = voice.locale
                    vLocale.language == targetLocale.language &&
                            (targetLocale.country.isEmpty() || vLocale.country == targetLocale.country)
                }.sortedWith(
                    compareByDescending<Voice> { voice ->
                        val nameLower = voice.name.lowercase()
                        when (gender) {
                            "M" -> if (nameLower.contains("male") || nameLower.contains("m-") || nameLower.contains("guy") || nameLower.contains("man") || nameLower.contains("mr.") || nameLower.contains("boy")) 1 else 0
                            "F" -> if (nameLower.contains("female") || nameLower.contains("f-") || nameLower.contains("girl") || nameLower.contains("woman") || nameLower.contains("ms.") || nameLower.contains("mrs.") || nameLower.contains("lady")) 1 else 0
                            else -> 0
                        }
                    }.thenByDescending { voice ->
                        val nameLower = voice.name.lowercase()
                        if (nameLower.contains("network") || nameLower.contains("natural") || nameLower.contains("wavenet") || nameLower.contains("neural")) 1 else 0
                    }
                ).firstOrNull()

                if (preferredVoice != null) {
                    tts?.voice = preferredVoice
                }
            }

            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utteranceId")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utteranceId")
        }
    }

    fun stop() {
        tts?.stop()
        tts?.shutdown()
    }
}

enum class Screen {
    Splash, Login, Secretary, Lesson, Performance
}

data class Message(
    val text: String,
    val isUser: Boolean,
    val translation: String? = null,
    val phonetics: String? = null,
    val vocabulary: String? = null,
    val targetPhrase: String? = null
)

@Stable
class UserSessionState(
    private val context: Context,
    initialLanguage: String = "Inglês",
    initialLevel: String = "Básico"
) {
    var userId by mutableStateOf<String?>(null)
    var userName by mutableStateOf<String?>(null)
    var language by mutableStateOf(initialLanguage)
    var level by mutableStateOf(initialLevel)
    var levelProgress by mutableIntStateOf(0)
    var streakCount by mutableIntStateOf(0)
    var lastStudyDate by mutableStateOf("")
    var totalTimeMillisAtLevel by mutableLongStateOf(0L)
    var totalSpeakingTimeMillisAtLevel by mutableLongStateOf(0L)
    var totalProfessorSpeakingTimeMillis by mutableLongStateOf(0L)
    var lessonStartTime by mutableStateOf(System.currentTimeMillis())
    var sessionGrade by mutableIntStateOf(0)

    // Sinalizadores de estado para a UI poder reagir a carregamento/erros
    var isLoading by mutableStateOf(false)
    var lastSyncError by mutableStateOf<String?>(null)

    private val db = Firebase.firestore

    fun clearPendingPronunciationState() {
        expectedPhrase = null
        waitingForPronunciation = false
        retryCount = 0
    }

    private fun getRequiredMillisForLevel(lvl: String): Long {
        val hours = when (lvl) {
            "Iniciante" -> 24
            "Básico" -> 30
            "Intermediário" -> 36
            "Avançado" -> 40
            "Fluente" -> 48
            "Nativo" -> 52
            else -> 24
        }
        return hours * 3600 * 1000L
    }

    private fun updateStudyTime() {
        val now = System.currentTimeMillis()
        val sessionDuration = now - lessonStartTime
        totalTimeMillisAtLevel += sessionDuration
        lessonStartTime = now

        // Nova métrica: (Tempo total assistido / Tempo necessário para o nível) * 100
        val required = getRequiredMillisForLevel(level)
        if (required > 0) {
            levelProgress = ((totalTimeMillisAtLevel.toFloat() / required.toFloat()) * 100).toInt().coerceAtMost(100)
        }
    }

    fun addSpeakingTime(millis: Long) {
        totalSpeakingTimeMillisAtLevel += millis
        saveProgress()
    }

    fun addProfessorSpeakingTime(millis: Long) {
        totalProfessorSpeakingTimeMillis += millis
        saveProgress()
    }

    fun resetTopicForNewLanguage(newLanguage: String) {
        if (language != newLanguage) {
            updateStudyTime()
            saveProgress()
            language = newLanguage
            loadProgress(newLanguage)
            lessonStartTime = System.currentTimeMillis()
        }
    }

    fun updateLevel(newLevel: String) {
        if (level != newLevel) {
            updateStudyTime()
            saveProgress()
            level = newLevel
            currentTopicIndex = 0
            interactionCount = 0
            lessonId = 1
            levelProgress = 0
            totalTimeMillisAtLevel = 0
            askedQuestionsSet.clear()
            clearPendingPronunciationState()
            saveProgress()
        }
    }

    fun checkAndUpdateStreak() {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())

        if (lastStudyDate != today) {
            val yesterday = sdf.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))
            if (lastStudyDate == yesterday) {
                streakCount++
            } else if (lastStudyDate.isNotEmpty()) {
                streakCount = 1
            } else {
                streakCount = 1
            }
            lastStudyDate = today
            saveProgress()
        }
    }

    fun isLessonTimeMet(): Boolean {
        val durationMs = (System.currentTimeMillis() - lessonStartTime) + totalTimeMillisAtLevel
        // Tempo mínimo reduzido para 30 minutos conforme nova diretriz pedagógica
        return durationMs >= 30 * 60 * 1000
    }

    fun getFormattedTotalTime(): String {
        val totalSecs = ((System.currentTimeMillis() - lessonStartTime) + totalTimeMillisAtLevel) / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        return String.format(Locale.getDefault(), "%02dh %02dm", hours, minutes)
    }

    fun getFormattedSpeakingTime(): String {
        val totalSecs = (totalSpeakingTimeMillisAtLevel + totalProfessorSpeakingTimeMillis) / 1000
        val minutes = totalSecs / 60
        val seconds = totalSecs % 60
        return String.format(Locale.getDefault(), "%02dm %02ds", minutes, seconds)
    }

    var currentTopicIndex by mutableIntStateOf(0)
    var interactionCount by mutableIntStateOf(0)
    var lessonId by mutableIntStateOf(1)
    var learnedWords = mutableStateSetOf<String>()
    var askedQuestionsSet = mutableStateSetOf<String>()

    var expectedPhrase by mutableStateOf<String?>(null)
    var waitingForPronunciation by mutableStateOf(false)
    var retryCount by mutableIntStateOf(0)

    fun getLanguageTopics(lang: String): Map<String, List<String>> {
        val japaneseTopics = mapOf(
            "Iniciante" to listOf(
                "Saudações e Apresentação", "Hiragana Básico", "Katakana Básico", "Números e Idade", "Datas, Dias e Horas", "Família e Relações", "Pronomes e Frases Simples", "Partículas Básicas", "Verbos Comuns no Presente", "Objetos e Sala", "Comida e Bebidas", "Perguntas Simples", "Rotina de Sobrevivência", "Cores", "Meios de Transporte", "Clima Básico", "Localização", "Compras Iniciais", "Saúde Básica", "Hobbies", "Natureza", "Festivais (Matsuri)", "Japão e Nacionalidades", "Revisão Iniciante"
            ),
            "Básico" to listOf(
                "Rotina Diária", "Compras e Preços", "Transporte e Deslocamento", "Casa e Ambiente", "Clima e Estações", "Gostos e Preferências", "Convites e Respostas", "Localização e Direções", "Escola e Trabalho", "Formas Polidas Básicas", "Adjetivos i e na", "Partículas Ni/E/De", "Verbos de Movimento", "Pedidos em Restaurante", "Horários de Funcionamento", "Descrição de Pessoas", "Corpo Humano", "Vestuário", "Dias da Semana e Meses", "Verbos de Existência", "Família (Honra/Humildade)", "Cidade e Bairro", "Passatempos", "Planos de Fim de Semana", "Atividades de Lazer", "Saúde e Consultas", "Emergências", "Correios e Bancos", "Tecnologia Básica", "Revisão Básico"
            ),
            "Intermediário" to listOf(
                "Passado e Experiências", "Futuro e Planos", "Comparações", "Saúde e Bem-estar", "Viagens", "Estudos", "Trabalho", "Tecnologia", "Mensagens e E-mails", "Opinião e Justificativa", "Narração de Eventos", "Explicação de Processos", "Cultura e Tradições", "Meio Ambiente", "Finanças Pessoais", "Artes e Entretenimento", "Ciência no Cotidiano", "Relacionamentos", "História e Biografias", "Gastronomia", "Moda e Tendências", "Educação", "Trabalho Remoto", "Desenvolvimento Pessoal", "Direitos e Deveres", "Urbanismo", "Lazer e Criatividade", "Festividades Culturais", "Vida Social", "Mídia e Notícias", "Serviços Públicos", "Habitação", "Interesses Especiais", "Futuro da Sociedade", "Ética", "Revisão Intermediário"
            ),
            "Avançado" to listOf(
                "Honoríficos (Keigo)", "Estruturas Complexas", "Resumos e Paráfrases", "Debates", "Notícias e Cultura", "Ambiente Profissional", "Apresentações", "Relações Sociais", "Literatura Curta", "Argumentação", "Expressões Idiomáticas", "Cultura Corporativa", "Temas Sociais", "Ética e Tecnologia", "Mudanças Climáticas", "Economia", "Política", "Inovação", "Psicologia", "Literatura Clássica", "Análise de Mídia", "Linguagem Jurídica", "Marketing", "Gestão de Conflitos", "Liderança", "Estratégia", "Cidades Inteligentes", "Futurismo", "Biotecnologia", "História da Arte", "Cinema e Crítica", "Religião", "Antropologia", "Sustentabilidade", "Poder e Influência", "Tradições Milenares", "Sociologia do Japão", "Arquitetura Moderna", "Gastronomia de Especialidade", "Revisão Avançado"
            ),
            "Fluente" to listOf(
                "Conversa Livre", "Entrevistas Simuladas", "Opiniões sobre Cultura", "Situações Espontâneas", "Reuniões e Trabalho", "Narrativas Pessoais", "Temas Abstratos", "Discussões Sociais", "Ajuste de Sotaque", "Naturalidade de Resposta", "Storytelling", "Debates Políticos", "Discussão de Filmes", "Nuances de Registro", "Ética na IA", "Globalização", "Educação Moderna", "Saúde Coletiva", "Tendências Futuras", "Storytelling Empresarial", "Resolução de Problemas", "Filosofia Moderna", "Sociologia Urbana", "Crítica Social", "Economia Comportamental", "Astronomia", "Psicologia Organizacional", "Gestão de Crises", "Diplomacia", "Jornalismo", "Literatura Contemporânea", "Arte Digital", "Esportes e Geopolítica", "Moda e Identidade", "Culinária e Antropologia", "Viagens de Imersão", "Equipes Multiculturais", "Futuro do Trabalho", "Direitos Humanos", "Evolução Tecnológica", "Neurociência", "Cibercultura", "Psicologia do Consumo", "Ecologia", "Urbanismo Sustentável", "Inteligência Coletiva", "Fronteiras da Ciência", "Revisão Fluente"
            ),
            "Nativo" to listOf(
                "Humor e Ironia", "Dialetos e Variações", "Escrita Formal e Criativa", "Debate Avançado", "Leitura de Ensaios", "Idiomatismos", "Registro Acadêmico", "Registro Corporativo", "Retórica", "Fluência Sem Esforço", "Persuasão", "Geopolítica", "Estratégia", "Inovação de Ponta", "Leitura Crítica", "Nuances de Entonação", "Alta Pressão", "Precisão Lexical", "Contextos Variados", "Humor Regional", "Cultura Pop", "História e Folclore", "Sistemas de Governo", "Clássicos Literários", "Valores Éticos", "Comunicação Não-Verbal", "Dialectologia", "Etimologia", "Poética e Métrica", "Teatro e Performance", "Discursos Históricos", "Tradução e Interpretação", "Terminologia Médica/Jurídica", "Psicanálise", "Teoria dos Jogos", "Complexidade Social", "Ecossistemas", "Física Teórica", "Engenharia", "Arquitetura", "Teoria Musical", "Estatística", "Religião Comparada", "Arqueologia", "Genealogia", "Oceanografia", "Exploração Espacial", "IA Forte", "Criptografia", "Mobilidade Urbana", "Filosofia da Linguagem", "Revisão Nível Nativo"
            )
        )

        val mandarinTopics = mapOf(
            "Iniciante" to listOf(
                "Pinyin e Tons", "Cumprimentos", "Apresentação Pessoal", "Números", "Datas e Horas", "Família", "Pronomes", "Estrutura Básica da Frase", "Verbos Comuns", "Objetos do Dia a Dia", "Comida e Bebida", "Perguntas Básicas", "Cores", "Nacionalidades", "Tempo e Clima", "Transporte Simples", "Localização", "Rotina", "Compras", "Partículas de Interrogação", "Classificadores Básicos", "Direções Simples", "Saúde Básica", "Revisão Iniciante"
            ),
            "Básico" to listOf(
                "Rotina Detalhada", "Casa e Cidade", "Compras e Negociação", "Transporte Público", "Clima e Estações", "Preferências e Gostos", "Pedidos em Restaurante", "Preços e Quantidades", "Direções e Mapas", "Polidez e Uso Social", "Verbos de Desejo", "Passado com Le", "Comparativos Básicos", "Hobbies", "Trabalho e Escritório", "Escola e Estudo", "Descrição de Objetos", "Vestuário", "Esportes", "Feriados Chineses", "Tecnologia Básica", "Internet", "Saúde e Farmácia", "Bancos e Dinheiro", "Viagens Curtas", "Cultura e Costumes", "Meios de Comunicação", "Família Alargada", "Natureza", "Revisão Básico"
            ),
            "Intermediário" to listOf(
                "Passado e Experiência", "Planos Futuros", "Comparação", "Viagem", "Saúde", "Trabalho", "Estudos", "Mensagens", "Redes sociais", "Expressar Opinião", "Narração de Eventos", "Explicação de Processos", "Cultura Tradicional", "Meio Ambiente", "Finanças Pessoais", "Artes e Entretenimento", "Ciência no Cotidiano", "Relacionamentos", "Biografias", "Gastronomia Chinesa", "Moda e Tendências", "Educação na China", "Trabalho Remoto", "Desenvolvimento Pessoal", "Direitos e Deveres", "Urbanismo", "Lazer e Criatividade", "História Contemporânea", "Vida Social", "Mídia e Notícias", "Serviços Públicos", "Habitação", "Interesses Especiais", "Futuro da Sociedade", "Ética", "Revisão Intermediário"
            ),
            "Avançado" to listOf(
                "Estruturas Longas", "Conectores Lógicos", "Debate", "Notícias", "Cultura", "Negócios", "Escrita Formal", "Expressões Idiomáticas (Chengyu)", "Apresentações", "Resumo de Textos", "Voz Passiva em Mandarim", "Discurso Indireto", "Condicionais", "Temas Sociais", "Ética e Tecnologia", "Mudanças Climáticas", "Economia da China", "Política Internacional", "Inovação", "Psicologia", "Literatura Clássica", "Análise de Mídia", "Linguagem Jurídica", "Marketing", "Gestão de Conflitos", "Liderança", "Estratégia Empresarial", "Cidades Inteligentes", "Futurismo", "Biotecnologia", "História da Arte", "Cinema e Crítica", "Religião", "Antropologia", "Sustentabilidade", "Poder e Influência", "Caligrafia e Arte", "Filosofia Chinesa", "Urbanismo Moderno", "Revisão Avançado"
            ),
            "Fluente" to listOf(
                "Conversa Livre", "Entrevistas", "Tópicos do Cotidiano", "Atualidades", "Opiniões", "Trabalho", "Cultura", "Improviso", "Pronúncia Fina", "Ritmo Natural", "Storytelling", "Debates Políticos", "Discussão de Filmes", "Nuances de Registro", "IA e Sociedade", "Globalização", "Educação Moderna", "Saúde Coletiva", "Tendências Futuras", "Storytelling Empresarial", "Resolução de Problemas", "Filosofia Contemporânea", "Sociologia Urbana", "Crítica Social", "Economia Comportamental", "Astronomia", "Psicologia Organizacional", "Gestão de Crises", "Diplomacia", "Jornalismo Investigativo", "Literatura Contemporânea", "Arte Digital", "Esportes e Geopolítica", "Moda e Identidade", "Culinária e Antropologia", "Viagens de Imersão", "Equipes Multiculturais", "Futuro do Trabalho", "Direitos Humanos", "Evolução Tecnológica", "Neurociência", "Cibercultura", "Psicologia do Consumo", "Ecologia", "Urbanismo Sustentável", "Inteligência Coletiva", "Fronteiras da Ciência", "Revisão Fluente"
            ),
            "Nativo" to listOf(
                "Registro Formal e Informal", "Idiomatismos", "Linguagem Acadêmica", "Negociação", "Persuasão", "Debate Avançado", "Cultura e Mídia", "Ambiguidade e Nuance", "Estilo Escrito", "Naturalidade de Expressão", "Geopolítica", "Estratégia Militar/Empresarial", "Inovação de Ponta", "Leitura Crítica", "Alta Pressão", "Precisão Lexical", "Contextos Variados", "Humor Regional", "História Milenar", "Sistemas de Governo", "Clássicos Confucionistas", "Valores Éticos", "Comunicação Não-Verbal", "Evolução da Língua", "Poética e Métrica", "Tradução Literária", "Terminologia Médica/Jurídica", "Psicanálise", "Teoria dos Jogos", "Complexidade Social", "Ecossistemas", "Física Teórica", "Engenharia Civil", "Arquitetura Tradicional", "Estatística", "Religião Comparada", "Arqueologia", "Genealogia", "Oceanografia", "Exploração Espacial", "Criptografia", "Mobilidade Urbana", "Filosofia da Linguagem", "Revisão Nível Nativo"
            )
        )

        val defaultTopics = mapOf(
            "Iniciante" to listOf(
                "Alfabeto e Sons", "Saudações e Cumprimentos", "Números e Idade", "Cores e Objetos", "Família e Parentes", 
                "Países e Nacionalidades", "Dias, Meses e Estações", "Verbo Ser/Estar (Base)", "Pronomes Pessoais", 
                "Artigos e Plural", "Perguntas Básicas", "Rotina Diária", "Horas e Agenda", "Clima e Tempo", 
                "Comidas e Bebidas", "Verbos de Ação Básicos", "Localização Espacial", "Sentimentos Simples", 
                "Vestuário", "Animais e Natureza", "Transportes", "Partes do Corpo", "Emergências e Saúde", "Revisão Iniciante"
            ),
            "Básico" to listOf(
                "Presente Simples", "There is / There are", "Adjetivos Possessivos", "Pronomes Demonstrativos", 
                "Preposições de Lugar", "Rotina e Hobbies", "Preferências e Gostos", "Casa e Objetos Domésticos", 
                "Compras e Preços", "Quantidades e Medidas", "Restaurante e Pedidos", "Trabalho e Profissões", 
                "Direções e Mapas", "Saúde e Sintomas", "Descrição de Pessoas", "Feriados e Celebrações", 
                "Habilidades (Can/Can't)", "Planos Futuros Simples", "Clima e Atividades", "Cotidiano no Escritório", 
                "Vida Social", "Tecnologia Básica", "Meios de Comunicação", "Viagens Curtas", "Esportes e Lazer", 
                "Música e Cinema", "Personalidades", "Cidades e Lugares", "Dinheiro e Bancos", "Revisão Básico"
            ),
            "Intermediário" to listOf(
                "Past Simple", "Past Continuous", "Future Forms (Will/Going to)", "Present Perfect Simple", 
                "Comparativos e Superlativos", "Modais de Obrigação", "Modais de Conselho", "Expressar Opinião", 
                "Concordância e Discordância", "Viagens e Aeroportos", "Estudos e Vida Acadêmica", "Carreira e Entrevistas", 
                "Tecnologia e Redes Sociais", "Atendimento ao Cliente", "Notícias e Mídia", "E-emails Profissionais", 
                "Relatórios e Mensagens", "Narração de Eventos", "Explicação de Processos", "Cultura e Tradições", 
                "Meio Ambiente", "Saúde e Bem-estar", "Finanças Pessoais", "Artes e Entretenimento", "Ciência no Cotidiano", 
                "Relacionamentos", "História e Biografias", "Gastronomia Avançada", "Moda e Tendências", 
                "Educação no Mundo", "Trabalho Remoto", "Desenvolvimento Pessoal", "Direitos e Deveres", 
                "Urbanismo", "Lazer e Criatividade", "Revisão Intermediário"
            ),
            "Avançado" to listOf(
                "Voz Passiva", "Discurso Indireto (Reported Speech)", "Condicionais Mistas", "Phrasal Verbs de Trabalho", 
                "Phrasal Verbs de Relacionamento", "Collocations Comuns", "Idiomatiscmos Base", "Debates de Tópicos Sociais", 
                "Textos Formais e Acadêmicos", "Apresentações Profissionais", "Reuniões e Negociações", "Argumentação Lógica", 
                "Contra-argumentação", "Resumo de Textos Longos", "Nuances de Entonação", "Escuta de Sotaques Variados", 
                "Temas Abstratos e Filosofia", "Ética e Tecnologia", "Mudanças Climáticas Profundo", "Economia Global", 
                "Política Internacional", "Inovação e Ciência", "Psicologia Humana", "Literatura Clássica", 
                "Análise de Poesia/Música", "Linguagem Jurídica Básica", "Marketing e Persuasão", "Gestão de Conflitos", 
                "Liderança", "Estratégia Empresarial", "Cidades Inteligentes", "Futurismo", "Genética e Biotecnologia", 
                "História da Arte", "Cinema e Crítica", "Religião e Sociedade", "Antropologia", "Sustentabilidade", 
                "Poder e Influência", "Revisão Avançado"
            ),
            "Fluente" to listOf(
                "Conversação Livre: Rotina", "Storytelling Pessoal", "Discussão: Cinema", "Discussão: Literatura", 
                "Análise de Notícias Atuais", "Debate: Tecnologia", "Debate: Cultura", "Debate: Sociedade", 
                "Entrevistas Simuladas", "Reuniões Profissionais Complexas", "Improviso em Crises", "Humor e Piadas", 
                "Ironia e Sarcasmo", "Ajuste de Ritmo de Fala", "Variações de Registro", "Debate sobre Globalização", 
                "Ética na Inteligência Artificial", "Sustentabilidade Global", "Análise de Discursos", "Persuasão em Vendas", 
                "Negociação de Contratos", "Debate: Educação Moderna", "Debate: Saúde Coletiva", "Análise de Tendências", 
                "Storytelling Empresarial", "Resolução de Problemas Complexos", "Discussão de Abstrações", "Filosofia Moderna", 
                "Sociologia Urbana", "Crítica Política", "Economia Comportamental", "Astronomia e Espaço", 
                "Psicologia Organizacional", "Gestão de Crises", "Diplomacia", "Jornalismo Investigativo", 
                "Literatura Contemporânea", "Arte Digital", "Esportes e Geopolítica", "Moda como Identidade", 
                "Culinária e Antropologia", "Viagens de Imersão", "Trabalho em Equipes Multiculturais", "Futuro do Trabalho", 
                "Direitos Humanos", "Evolução Tecnológica", "Neurociência", "Cibercultura", "Psicologia do Consumo", 
                "Ecologia", "Urbanismo Sustentável", "Inteligência Coletiva", "Fronteiras da Ciência", "Revisão Fluente"
            ),
            "Nativo" to listOf(
                "Variações Regionais: EUA", "Variações Regionais: Reino Unido", "Variações Regionais: Austrália/Canadá", 
                "Gírias e Expressões Locais", "Linguagem Informal e Slang", "Sotaques Profundos", "Ironia Cultural", 
                "Expressões Idiomáticas Arcaicas", "Retórica e Persuasão Avançada", "Escrita Acadêmica Refinada", 
                "Escrita Profissional de Alto Nível", "Discussão de Filosofia Profunda", "Geopolítica e Estratégia", 
                "Ciência e Inovação de Ponta", "Leitura Crítica de Artigos", "Nuances de Entonação e Ênfase", 
                "Fluidez sob Alta Pressão", "Precisão Lexical Absoluta", "Naturalidade em Contextos Variados", 
                "Humor Regional", "Referencial Cultural Pop", "História e Folclore", "Política e Sistemas de Governo", 
                "Análise de Clássicos Literários", "Escrita Criativa", "Debate de Valores Éticos", "Comunicação Não-Verbal", 
                "Dialetos e Dialectologia", "Etimologia e Evolução da Língua", "Poética e Métrica", "Teatro e Performance", 
                "Discursos Marcantes da História", "Tradução e Interpretação", "Terminologia Médica/Jurídica Específica", 
                "Psicanálise e Linguagem", "Teoria dos Jogos", "Complexidade Social", "Ecossistemas e Biomas", 
                "Física Teórica", "Engenharia e Arquitetura", "Música e Teoria Musical", "Gastronomia de Especialidade", 
                "Esportes e Estatística", "Religião Comparada", "Arqueologia", "Genealogia", "Oceanografia", 
                "Exploração Espacial", "Inteligência Artificial Forte", "Criptografia", "Urbanismo e Mobilidade", "Revisão Nível Nativo"
            )
        )

        val russianTopics = mapOf(
            "Iniciante" to listOf(
                "Alfabeto Cirílico", "Sons e Pronúncia", "Saudações", "Apresentação Pessoal", "Números", "Idade e Telefone", "Família", "Dias e Meses", "Cores e Objetos", "Perguntas Básicas", "Tempo e Clima", "Frases de Sobrevivência", "Saúde Básica", "Transporte", "Localização", "Compras Iniciais", "Culinária Russa", "Rússia e Cultura", "Natureza", "Hobbies", "Escritura Cirílica", "Artigos e Plural", "Verbos de Ação", "Revisão Iniciante"
            ),
            "Básico" to listOf(
                "Gênero e Número", "Ser e Estar Implícitos", "Rotina Diária", "Casa e Mobiliário", "Comida e Bebida", "Compras e Preços", "Direções e Transporte", "Horários", "Descrição de Pessoas", "Preferências e Gostos", "Partículas de Negação", "Pronomes Possessivos", "Adjetivos Básicos", "Trabalho e Profissões", "Cidade e Lugares", "Saúde e Sintomas", "Viagens Curtas", "Atividades de Lazer", "Vida Social", "Tecnologia Básica", "Comunicação", "Dinheiro", "Feriados Russos", "Música e Cinema", "Esportes", "Personalidades", "História Básica", "Geografia", "Educação", "Revisão Básico"
            ),
            "Intermediário" to listOf(
                "Passado e Passado Habitual", "Futuro e Intenção", "Viagens", "Saúde", "Trabalho", "Estudos", "Compras e Serviços", "Mensagens e E-mails", "Cultura", "Opiniões e Comparações", "Casos Básicos", "Narração de Eventos", "Explicação de Processos", "Tecnologia", "Meio Ambiente", "Finanças", "Artes", "Ciência", "Relacionamentos", "Biografias", "Gastronomia", "Moda", "Desenvolvimento Pessoal", "Direitos e Deveres", "Urbanismo", "Lazer", "Mídia", "Serviços Públicos", "Habitação", "Interesses Especiais", "Futuro", "Ética", "Trabalho Remoto", "Educação Global", "História", "Revisão Intermediário"
            ),
            "Avançado" to listOf(
                "Casos em Uso Real", "Verbos de Movimento", "Conectores e Coesão", "Discurso Indireto", "Notícias e Atualidades", "Debates", "Textos Formais", "Literatura Curta", "Ambiente Profissional", "Argumentação", "Estruturas Complexas", "Resumos", "Paráfrases", "Estilo e Registro", "Temas Sociais", "Ética Tecnológica", "Mudanças Climáticas", "Economia Global", "Política", "Inovação", "Psicologia", "Linguagem Jurídica", "Marketing", "Gestão", "Liderança", "Estratégia", "Cidades Inteligentes", "Futurismo", "Biotecnologia", "História da Arte", "Cinema", "Antropologia", "Sustentabilidade", "Poder", "Tradições", "Sociologia", "Arquitetura", "Religião", "Geopolítica", "Revisão Avançado"
            ),
            "Fluente" to listOf(
                "Conversa Livre", "Histórias Pessoais", "Opiniões Sociais", "Cultura e Mídia", "Trabalho e Carreira", "Reuniões e Apresentações", "Entrevistas Simuladas", "Improviso", "Discussões Abstratas", "Ajuste de Entonação e Ritmo", "Storytelling", "Debate Crítico", "Globalização", "Ética na IA", "Educação Moderna", "Saúde Coletiva", "Tendências", "Resolução de Problemas", "Filosofia Moderna", "Sociologia Urbana", "Crítica Social", "Economia Comportamental", "Astronomia", "Psicologia Organizacional", "Gestão de Crises", "Diplomacia", "Jornalismo", "Literatura Contemporânea", "Arte Digital", "Esportes e Geopolítica", "Moda", "Identidade", "Culinária", "Antropologia", "Viagens", "Equipes Multiculturais", "Direitos Humanos", "Neurociência", "Cibercultura", "Ecologia", "Sustentabilidade", "Ciência", "Poder e Influência", "Futuro", "Inovação", "Mundo Digital", "Cidadania", "Revisão Fluente"
            ),
            "Nativo" to listOf(
                "Humor e Ironia", "Idiomatismos", "Registro Formal e Informal", "Redação Sofisticada", "Debate Avançado", "Retórica", "Leitura Crítica", "Variações Culturais", "Expressão Persuasiva", "Fluência Sem Esforço", "Nuances de Registro", "Escolha Estilística", "Dialetos", "Escrita Criativa", "Linguagem Acadêmica", "Negociação", "Persuasão", "Mídia e Sociedade", "Geopolítica", "Inovação Profunda", "Alta Pressão", "Precisão Lexical", "Contextos Variados", "Humor Regional", "Cultura Pop", "História Milenar", "Sistemas de Governo", "Clássicos Literários", "Valores Éticos", "Comunicação Não-Verbal", "Etimologia", "Poética", "Teatro", "Discursos Históricos", "Tradução", "Terminologia Médica/Jurídica", "Psicanálise", "Teoria dos Jogos", "Complexidade Social", "Ecossistemas", "Física Teórica", "Engenharia", "Arquitetura", "Música", "Estatística", "Religião", "Arqueologia", "Genealogia", "Oceanografia", "Exploração Espacial", "Criptografia", "Mobilidade", "Filosofia", "Revisão Nível Nativo"
            )
        )

        return when (lang) {
            "Japonês" -> japaneseTopics
            "Chinês" -> mandarinTopics
            "Russo" -> russianTopics
            else -> defaultTopics
        }
    }

    /**
     * Estrutura corrigida: users_stats/{uid}/languages/{idioma}
     * - "users_stats" é a coleção raiz
     * - {uid} é o documento do usuário (uma linha por usuário)
     * - "languages" é a subcoleção dentro do documento do usuário
     * - {idioma} é o documento de progresso para aquele idioma específico
     */
    private fun languageDocRef(uid: String, lang: String) =
        db.collection("users_stats")
            .document(uid)
            .collection("languages")
            .document(lang)

    fun loadProgress(lang: String) {
        val uid = userId ?: return
        val scope = (context as? ComponentActivity)?.lifecycleScope ?: return
        isLoading = true
        lastSyncError = null
        scope.launch {
            try {
                val doc = languageDocRef(uid, lang).get().await()

                if (doc.exists()) {
                    levelProgress = (doc.getLong("level_progress") ?: doc.getLong("xp") ?: 0L).toInt()
                    level = doc.getString("nivel") ?: "Iniciante"
                    lessonId = (doc.getLong("lesson_id") ?: 1L).toInt()
                    currentTopicIndex = (doc.getLong("topic_index") ?: 0L).toInt()

                    val streakStr = doc.getString("streak") ?: "0"
                    streakCount = streakStr.toIntOrNull() ?: 0
                    lastStudyDate = doc.getString("last_study_date") ?: ""

                    totalTimeMillisAtLevel = doc.getLong("time_at_level_raw") ?: 0L
                    totalSpeakingTimeMillisAtLevel = doc.getLong("speaking_time_raw") ?: 0L
                    totalProfessorSpeakingTimeMillis = doc.getLong("professor_speaking_time_raw") ?: 0L

                    // Os campos first_lesson_date e level_completion_date_* já são carregados via Firestore
                    // e preservados durante o saveProgress()

                    val learned = doc.get("learned_words") as? List<String> ?: emptyList()
                    learnedWords.clear()
                    learnedWords.addAll(learned)

                    // asked_questions não é persistido no Firestore — fica só em memória,
                    // por isso o set é reiniciado a cada carregamento de progresso.
                    askedQuestionsSet.clear()
                    android.util.Log.d("UserSessionState", "✅ Progresso carregado de users_stats/$uid/languages/$lang")
                } else {
                    android.util.Log.d("UserSessionState", "ℹ️ Nenhum progresso salvo ainda para $lang (documento não existe). Usando padrões.")
                }
            } catch (e: Exception) {
                android.util.Log.e("UserSessionState", "⚠️ Erro ao carregar do Firestore (usando local): ${e.message}", e)
                lastSyncError = e.message
                try {
                    val prefs = context.dataStore.data.first()
                    levelProgress = prefs[intPreferencesKey("${lang}_progress")] ?: 0
                    level = prefs[stringPreferencesKey("${lang}_level")] ?: "Iniciante"
                } catch (e2: Exception) {
                    android.util.Log.e("UserSessionState", "⚠️ Erro também no fallback local: ${e2.message}", e2)
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun getCurrentTopicName(): String {
        val topicsMap = getLanguageTopics(language)
        val levelTopics = topicsMap[level] ?: topicsMap["Iniciante"]!!
        return levelTopics[currentTopicIndex % levelTopics.size]
    }

    fun markLearned(word: String) {
        learnedWords.add(word.lowercase())
        saveProgress()
    }

    fun markAsked(questionId: String) {
        askedQuestionsSet.add(questionId)
        saveProgress()
    }

    fun isAsked(questionId: String) = askedQuestionsSet.contains(questionId)

    fun nextLesson() {
        // Agora permite avançar mesmo se o tempo não foi atingido, 
        // mas mantém a lógica de registro de tempo.
        lessonId++
        retryCount = 0
        val topicsMap = getLanguageTopics(language)
        val levelTopics = topicsMap[level] ?: topicsMap["Iniciante"]!!
        currentTopicIndex = (currentTopicIndex + 1) % levelTopics.size
        interactionCount = 0
        
        // Salva o progresso do tempo acumulado antes de resetar o início da aula
        updateStudyTime()
        lessonStartTime = System.currentTimeMillis()

        clearPendingPronunciationState()
        saveProgress()
    }

    fun saveProgress() {
        val uid = userId ?: run {
            android.util.Log.w("UserSessionState", "⚠️ saveProgress chamado sem userId definido — gravação ignorada.")
            return
        }
        val scope = (context as? ComponentActivity)?.lifecycleScope ?: return
        val lang = language

        updateStudyTime()
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val formattedDate = sdf.format(Date())

        val stats = hashMapOf(
            "user_id" to uid,
            "language" to lang,
            "last_study_date" to formattedDate,
            "nivel" to level,
            "streak" to streakCount.toString(),
            "tempo_aula" to getFormattedTotalTime(),
            "tempo_fala" to getFormattedSpeakingTime(),
            "level_progress" to levelProgress,
            "lesson_id" to lessonId,
            "topic_index" to currentTopicIndex,
            "time_at_level_raw" to totalTimeMillisAtLevel,
            "speaking_time_raw" to totalSpeakingTimeMillisAtLevel,
            "professor_speaking_time_raw" to totalProfessorSpeakingTimeMillis,
            "learned_words" to learnedWords.toList(),
            "updated_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        scope.launch {
            try {
                // Tenta buscar o documento primeiro para verificar o first_lesson_date
                val currentDoc = languageDocRef(uid, lang).get().await()
                if (!currentDoc.exists() || currentDoc.getString("first_lesson_date") == null) {
                    stats["first_lesson_date"] = formattedDate
                } else {
                    // Preserva a data original se ela já existir
                    stats["first_lesson_date"] = currentDoc.getString("first_lesson_date")!!
                }

                // Se o progresso atingir 100%, registramos a conclusão do nível
                if (levelProgress >= 100) {
                    stats["level_completion_date_$level"] = formattedDate
                }

                languageDocRef(uid, lang)
                    .set(stats, SetOptions.merge())
                    .await()

                lastSyncError = null
                android.util.Log.d("UserSessionState", "✅ Gravado em users_stats/$uid/languages/$lang")
            } catch (e: Exception) {
                android.util.Log.e("UserSessionState", "❌ Erro ao gravar progresso: ${e.message}", e)
                lastSyncError = e.message
                
                // Fallback local - aqui estamos dentro de scope.launch (contexto de suspensão)
                try {
                    context.dataStore.edit { prefs ->
                        prefs[intPreferencesKey("${lang}_progress")] = levelProgress
                        prefs[stringPreferencesKey("${lang}_level")] = level
                        prefs[longPreferencesKey("${lang}_timeAtLevel")] = totalTimeMillisAtLevel
                    }
                } catch (e2: Exception) {
                    android.util.Log.e("UserSessionState", "❌ Erro no fallback local: ${e2.message}", e2)
                }
            }
        }
    }

    /**
     * Busca estatísticas de todos os idiomas para o gráfico de performance
     */
    fun fetchAllStats(onResult: (List<Map<String, Any>>) -> Unit) {
        val uid = userId ?: return
        db.collection("users_stats").document(uid).collection("languages").get()
            .addOnSuccessListener { result ->
                val statsList = result.documents.mapNotNull { it.data }
                onResult(statsList)
            }
    }
}

data class Teacher(
    val name: String,
    val photoRes: Int,
    val photoTalkingRes: Int,
    val subject: String,
    val locale: Locale,
    val gender: String
)

@Composable
fun TalkingAvatar(isSpeaking: Boolean, normalRes: Int, talkingRes: Int, contentDescription: String, modifier: Modifier = Modifier) {
    var showTalkingImage by remember { mutableStateOf(false) }
    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            while (isSpeaking) {
                showTalkingImage = !showTalkingImage
                delay(200)
            }
        } else {
            showTalkingImage = false
        }
    }
    Image(
        painter = painterResource(id = if (showTalkingImage) talkingRes else normalRes),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.Splash) }
    val userSession = remember { UserSessionState(context) }
    var hasInitialGreetingHappened by remember { mutableStateOf(false) }

    Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
        when (screen) {
            Screen.Splash -> SplashScreen {
                val currentUser = Firebase.auth.currentUser
                if (currentUser != null) {
                    userSession.userId = currentUser.uid
                    userSession.userName = currentUser.displayName
                    userSession.loadProgress(userSession.language)
                    currentScreen = Screen.Secretary
                } else {
                    currentScreen = Screen.Login
                }
            }
            Screen.Login -> LoginScreen(onLoginSuccess = { user ->
                userSession.userId = user.uid
                userSession.userName = user.displayName
                userSession.loadProgress(userSession.language)
                currentScreen = Screen.Secretary
            })
            Screen.Secretary -> SecretaryScreen(
                userSession = userSession,
                isInitialGreeting = !hasInitialGreetingHappened,
                onGreetingDone = { hasInitialGreetingHappened = true },
                onNext = { currentScreen = Screen.Lesson },
                onViewPerformance = { currentScreen = Screen.Performance }
            )
            Screen.Performance -> PerformanceScreen(userSession = userSession, onBack = { currentScreen = Screen.Secretary })
            Screen.Lesson -> LessonScreen(userSession) { currentScreen = Screen.Secretary }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) { delay(2500); onTimeout() }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SEAQUAKE I.A",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF512DA8),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Language Course",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF512DA8),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Image(painter = painterResource(id = R.drawable.ia_course), contentDescription = "Logo", modifier = Modifier.size(200.dp))
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (com.google.firebase.auth.FirebaseUser) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    val auth = Firebase.auth
    val context = LocalContext.current

    // Configuração Google Sign-In - Usando ID fixo para evitar erro de recurso
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("379689111004-njfk8u8mh3q16i8nlvtuc35uuilr4u42.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        val user = auth.currentUser!!
                        val userData = hashMapOf(
                            "uid" to user.uid,
                            "email" to user.email,
                            "displayName" to user.displayName,
                            "lastLogin" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        Firebase.firestore.collection("users").document(user.uid)
                            .set(userData, SetOptions.merge())
                        onLoginSuccess(user)
                    } else {
                        val error = authTask.exception?.message ?: "Erro desconhecido"
                        android.util.Log.e("LoginScreen", "Erro Firebase Auth: $error")
                        Toast.makeText(context, "Falha Firebase Auth: $error", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: ApiException) {
                val detailedError = "Erro Google API: Code ${e.statusCode} | Status: ${com.google.android.gms.common.api.CommonStatusCodes.getStatusCodeString(e.statusCode)}"
                android.util.Log.e("LoginScreen", detailedError)
                Toast.makeText(context, detailedError, Toast.LENGTH_LONG).show()
            }
        } else {
            val errorMsg = "Login cancelado (Código 0). Isso geralmente é erro de SHA-1 ou configuração no Console Google Cloud."
            android.util.Log.e("LoginScreen", errorMsg)
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3E5F5)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(painter = painterResource(id = R.drawable.ia_course), contentDescription = "Logo", modifier = Modifier.size(120.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = if (isSignUp) "Crie sua conta" else "Acesse sua conta", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF512DA8))
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            fun saveUserToFirestore(user: com.google.firebase.auth.FirebaseUser) {
                val userData = hashMapOf(
                    "uid" to user.uid,
                    "email" to user.email,
                    "displayName" to user.displayName,
                    "lastLogin" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                Firebase.firestore.collection("users").document(user.uid)
                    .set(userData, SetOptions.merge())
            }

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) return@Button
                    if (isSignUp) {
                        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser!!
                                saveUserToFirestore(user)
                                onLoginSuccess(user)
                            } else {
                                Toast.makeText(context, "Erro: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser!!
                                saveUserToFirestore(user)
                                onLoginSuccess(user)
                            } else {
                                Toast.makeText(context, "Erro: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF512DA8))
            ) {
                Text(if (isSignUp) "Cadastrar" else "Entrar", color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF512DA8))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = "https://w7.pngwing.com/pngs/326/85/png-transparent-google-logo-google-text-trademark-logo-thumbnail.png",
                        contentDescription = "Google Logo",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Entrar com Google", color = Color(0xFF512DA8), fontWeight = FontWeight.Bold)
                }
            }

            TextButton(onClick = { isSignUp = !isSignUp }) {
                Text(if (isSignUp) "Já tem conta? Faça login" else "Novo por aqui? Cadastre-se")
            }
        }
    }
}

@Composable
fun SecretaryScreen(
    userSession: UserSessionState,
    isInitialGreeting: Boolean,
    onGreetingDone: () -> Unit,
    onNext: () -> Unit,
    onViewPerformance: () -> Unit
) {
    val context = LocalContext.current
    val voiceManager = LocalVoiceManager.current
    val geminiService = LocalGeminiService.current
    val isSpeaking by voiceManager.isSpeaking
    val languages = listOf("Inglês", "Espanhol", "Chinês", "Japonês", "Alemão", "Italiano", "Francês", "Russo")
    val levels = listOf("Iniciante", "Básico", "Intermediário", "Avançado", "Fluente", "Nativo")

    LaunchedEffect(userSession.language) {
        geminiService.clearHistory()
    }

    LaunchedEffect(Unit) {
        userSession.checkAndUpdateStreak()
        delay(500)
        val namePart = if (!userSession.userName.isNullOrBlank()) ", ${userSession.userName}" else ""
        if (isInitialGreeting) {
            voiceManager.speak("Olá$namePart! Sou sua assistente Sea. Vi que você parou no nível ${userSession.level} de ${userSession.language}. Qual curso faremos hoje?", locale = Locale("pt", "BR"), gender = "F")
            onGreetingDone()
        } else {
            voiceManager.speak("Vai aprender outra língua ou vai sair do app?", locale = Locale("pt", "BR"), gender = "F")
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3E5F5)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Card(modifier = Modifier.size(110.dp), shape = CircleShape, elevation = CardDefaults.cardElevation(6.dp)) {
                TalkingAvatar(isSpeaking, R.drawable.photo_secretary, R.drawable.photo_secretary_talking, "Secretary", Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "● AO VIVO", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(text = stringResource(id = R.string.secretary_name), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF512DA8))
            Spacer(modifier = Modifier.height(16.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Nível: ${userSession.level}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF512DA8))
                    Text(text = "Tempo Total: ${userSession.getFormattedTotalTime()}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { userSession.levelProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF4CAF50),
                        trackColor = Color(0xFFE0E0E0),
                    )
                    Text(text = "Progresso do Nível: ${userSession.levelProgress}%", fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))

                    if (userSession.streakCount > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "🔥 ${userSession.streakCount} dias!", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFF57C00))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            SelectionDropdown("Idioma", languages, userSession.language) { userSession.resetTopicForNewLanguage(it) }
            Spacer(modifier = Modifier.height(8.dp))
            SelectionDropdown("Nível", levels, userSession.level) { userSession.updateLevel(it) }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF512DA8))) {
                Text("Iniciar Aula", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = onViewPerformance,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                border = BorderStroke(1.dp, Color(0xFF512DA8)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
            ) {
                Icon(Icons.Default.Assessment, contentDescription = null, tint = Color(0xFF512DA8))
                Spacer(Modifier.width(8.dp))
                Text("Ver Meu Desempenho", color = Color(0xFF512DA8), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    (context as? Activity)?.let {
                        it.finishAndRemoveTask()
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF69B4), // Cor Rosa (Hot Pink)
                    contentColor = Color.White
                )
            ) {
                Text("Fechar Aplicativo", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(userSession: UserSessionState, onBack: () -> Unit) {
    var allStats by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        userSession.fetchAllStats { stats ->
            // Filtra idiomas que não tiveram aula ou têm tempo de fala inferior a 1 minuto (60000ms)
            allStats = stats.filter { stat ->
                val timeMs = stat["time_at_level_raw"] as? Long ?: 0L
                val speakMs = (stat["speaking_time_raw"] as? Long ?: 0L) + (stat["professor_speaking_time_raw"] as? Long ?: 0L)
                timeMs > 0 && speakMs >= 60000
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Minha Performance", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF512DA8),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF3E5F5)
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF512DA8))
            }
        } else if (allStats.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nenhum dado de performance encontrado.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Evolução por Idioma", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF512DA8))
                }

                items(allStats) { stat ->
                    val lang = stat["language"] as? String ?: "Desconhecido"
                    val level = stat["nivel"] as? String ?: "N/A"
                    val timeAtLevel = stat["tempo_aula"] as? String ?: "00h 00m"
                    val speakingTime = stat["tempo_fala"] as? String ?: "00m 00s"
                    val xp = ((stat["level_progress"] as? Long) ?: (stat["xp"] as? Long) ?: 0L).toInt()
                    val firstDate = stat["first_lesson_date"] as? String ?: "-"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = lang, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF512DA8))
                                Spacer(Modifier.weight(1f))
                                Text(text = "Nível: $level", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(text = "Início: $firstDate", fontSize = 12.sp, color = Color.Gray)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF3E5F5))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Tempo de Aula", fontSize = 11.sp, color = Color.Gray)
                                    Text(timeAtLevel, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Tempo de Fala", fontSize = 11.sp, color = Color.Gray)
                                    Text(speakingTime, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF4CAF50))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Conclusão", fontSize = 11.sp, color = Color.Gray)
                                    Text("$xp%", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFF57C00))
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            // Métrica de lições concluídas
                            val lessonId = (stat["lesson_id"] as? Long)?.toInt() ?: 1
                            val topicsMap = userSession.getLanguageTopics(lang)
                            val levelTopics = topicsMap[level] ?: topicsMap["Iniciante"] ?: emptyList()
                            val totalLessons = levelTopics.size
                            
                            val progressFactor = xp.toFloat() / 100f
                            // A lição atual é a lição em que o usuário está, mas o progresso temporal 
                            // pode indicar que ele já avançou "parte" da lição atual ou da próxima.
                            // Vamos usar a lição base + o progresso para mostrar o posicionamento.
                            val currentLessonPos = lessonId.toFloat() + (progressFactor * (1f / totalLessons.coerceAtLeast(1).toFloat()))
                            
                            Text("Posicionamento de Lições ($lessonId de $totalLessons)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(Modifier.height(8.dp))
                            
                            Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                                // Trilho de fundo
                                Box(modifier = Modifier.fillMaxWidth().height(8.dp).align(Alignment.Center).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE0E0E0)))
                                
                                // Pontos das lições
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    for (i in 1..totalLessons) {
                                        val isCompleted = i < lessonId
                                        val isCurrent = i == lessonId
                                        val color = if (isCompleted) Color(0xFF4CAF50) else if (isCurrent) Color(0xFF512DA8) else Color.LightGray
                                        val size = if (isCurrent) 14.dp else 10.dp
                                        
                                        Box(
                                            modifier = Modifier
                                                .size(size)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                    }
                                }
                            }
                            
                            Text(
                                text = "Você concluiu $lessonId lições e está avançando no conteúdo.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
                
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Datas de Conclusão", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF512DA8))
                }
                
                items(allStats) { stat ->
                    val lang = stat["language"] as? String ?: ""
                    val completions = stat.filterKeys { it.startsWith("level_completion_date_") }
                    
                    if (completions.isNotEmpty()) {
                        completions.forEach { (key, date) ->
                            val levelName = key.removePrefix("level_completion_date_")
                            Text(
                                text = "✅ $lang ($levelName): $date",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectionDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedCard(modifier = Modifier.fillMaxWidth().clickable { expanded = true }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = label, fontSize = 12.sp, color = Color.Gray)
                    Text(text = selected, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Icon(Icons.Default.ArrowDropDown, null)
            }
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onSelect(it); expanded = false }) }
        }
    }
}

@Composable
fun LessonScreen(userSession: UserSessionState, onBack: () -> Unit) {
    val context = LocalContext.current
    val voiceManager = LocalVoiceManager.current
    val geminiService = LocalGeminiService.current
    val scope = rememberCoroutineScope()
    val chatMessages = remember { mutableStateListOf<Message>() }
    var inputText by remember { mutableStateOf("") }
    var partialTranscript by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isSpeaking by voiceManager.isSpeaking
    var isListening by remember { mutableStateOf(false) }
    var speakingStartTime by remember { mutableLongStateOf(0L) }

    val teachers = mapOf(
        "Inglês" to Teacher("Sarah", R.drawable.photo_sarah, R.drawable.photo_sarah_talking, "Inglês", Locale.US, "F"),
        "Espanhol" to Teacher("Alex", R.drawable.photo_alex, R.drawable.photo_alex_talking, "Espanhol", Locale("es", "ES"), "M"),
        "Chinês" to Teacher("Li", R.drawable.photo_li, R.drawable.photo_li_talking, "Chinês", Locale.CHINA, "M"),
        "Japonês" to Teacher("Mei", R.drawable.photo_mei, R.drawable.photo_mei_talking, "Japonês", Locale.JAPAN, "F"),
        "Alemão" to Teacher("Hans", R.drawable.photo_hans, R.drawable.photo_hans_talking, "Alemão", Locale.GERMANY, "M"),
        "Italiano" to Teacher("Giulia", R.drawable.photo_gulia, R.drawable.photo_guila_talking, "Italiano", Locale.ITALY, "F"),
        "Francês" to Teacher("Claire", R.drawable.photo_claire, R.drawable.photo_claire_talking, "Francês", Locale.FRANCE, "F"),
        "Russo" to Teacher("Ivank", R.drawable.photo_ivank, R.drawable.photo_ivank_talking, "Russo", Locale("ru", "RU"), "M")
    )
    val currentTeacher = teachers[userSession.language] ?: teachers["Inglês"]!!

    DisposableEffect(voiceManager) {
        voiceManager.onSpeakingFinished = { duration ->
            userSession.addProfessorSpeakingTime(duration)
        }
        onDispose {
            voiceManager.onSpeakingFinished = null
        }
    }

    LaunchedEffect(Unit) {
        delay(1000)
        val professorTitle = if (currentTeacher.gender == "F") "sua professora" else "seu professor"

        val welcomeMessage = when(userSession.language) {
            "Inglês" -> "Hello! I am your English teacher, Sarah. Let's start our lesson!"
            "Espanhol" -> "¡Hola! Soy tu professor de Español, Alex. ¡Empecemos nuestra lección!"
            "Chinês" -> "你好！我是你的汉语老师李。让我们开始上课吧！"
            "Japonês" -> "こんにちは！私は日本語の先生の美です. レッスンを始めましょう！"
            "Alemão" -> "Hallo! Ich bin dein Deutschlehrer, Hans. Fangen wir an!"
            "Italiano" -> "Ciao! Sono la tua insegnante di italiano, Giulia. Cominciamo!"
            "Francês" -> "Bonjour! Je suis ton professor de français, Claire. Commençons!"
            "Russo" -> "Привет! Я твой учитель русского языка, Иванк. Давай начнем!"
            else -> "Hello!"
        }

        val welcomeTranslation = "Olá! Sou $professorTitle de ${userSession.language}. Já configurei nosso curso para o nível ${userSession.level}. Vamos praticar? 🚀"

        chatMessages.add(Message(text = welcomeMessage, isUser = false, translation = welcomeTranslation))
        voiceManager.speak(welcomeMessage, currentTeacher.locale, currentTeacher.gender)
    }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val recognitionIntent = remember(userSession.language) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Alterado para free form mas otimizado para reconhecimento de termos curtos
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val lang = when(userSession.language) {
                "Inglês" -> "en-US"
                "Espanhol" -> "es-ES"
                "Chinês" -> "zh-CN"
                "Japonês" -> "ja-JP"
                "Alemão" -> "de-DE"
                "Italiano" -> "it-IT"
                "Francês" -> "fr-FR"
                "Russo" -> "ru-RU"
                else -> "pt-BR"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            // Melhora a captura de falas curtas (como uma única letra)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isListening = true
            speechRecognizer.startListening(recognitionIntent)
        } else {
            Toast.makeText(context, "Permissão de áudio negada", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                if (speakingStartTime > 0) {
                    val duration = System.currentTimeMillis() - speakingStartTime
                    userSession.addSpeakingTime(duration)
                    speakingStartTime = 0
                }
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                if (text.isNotBlank()) {
                    chatMessages.add(Message(text, true))
                    processLearningMachineResponse(context, text, userSession, currentTeacher, chatMessages, voiceManager, geminiService, scope, listState)
                }
                isListening = false
                partialTranscript = ""
            }
            override fun onReadyForSpeech(p0: Bundle?) {
                speakingStartTime = System.currentTimeMillis()
                partialTranscript = ""
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {
                if (speakingStartTime > 0) {
                    val duration = System.currentTimeMillis() - speakingStartTime
                    userSession.addSpeakingTime(duration)
                    speakingStartTime = 0
                }
                isListening = false
            }
            override fun onError(error: Int) {
                if (speakingStartTime > 0) {
                    val duration = System.currentTimeMillis() - speakingStartTime
                    userSession.addSpeakingTime(duration)
                    speakingStartTime = 0
                }
                isListening = false
                partialTranscript = ""
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Erro de áudio"
                    SpeechRecognizer.ERROR_CLIENT -> "Erro no cliente (Verifique o Google App)"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissões insuficientes"
                    SpeechRecognizer.ERROR_NETWORK -> "Erro de rede"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tempo de rede esgotado"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Não entendi o que você disse"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Serviço ocupado"
                    SpeechRecognizer.ERROR_SERVER -> "Erro no servidor"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tempo esgotado (fale mais alto ou mais perto)"
                    else -> "Erro desconhecido ($error)"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                if (partial.isNotBlank()) {
                    partialTranscript = partial
                }
            }
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
        onDispose { speechRecognizer.destroy() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFEDF2F9)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    TalkingAvatar(
                        isSpeaking = isSpeaking,
                        normalRes = currentTeacher.photoRes,
                        talkingRes = currentTeacher.photoTalkingRes,
                        contentDescription = currentTeacher.name,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                    startY = 300f
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        val title = if (currentTeacher.gender == "F") "Professora" else "Professor"
                        Text(
                            text = "$title ${currentTeacher.name}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Aula de ${currentTeacher.subject} • Nível ${userSession.level}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Text(
                                text = "Duração: ${userSession.getFormattedTotalTime()}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Fala: ${userSession.getFormattedSpeakingTime()}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { userSession.levelProgress / 100f },
                            modifier = Modifier.fillMaxWidth(0.8f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF4CAF50),
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }

                    if (userSession.sessionGrade > 0) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                            color = Color(0xFFFFD700),
                            shape = CircleShape,
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = "${userSession.sessionGrade}",
                                modifier = Modifier.padding(8.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            userSession.clearPendingPronunciationState()
                            userSession.saveProgress()
                            voiceManager.speak("Quer aprender uma nova língua ou sair?", locale = Locale("pt", "BR"), gender = "F")
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF25C54)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text("FINALIZAR AULA", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Rolagem automática para a última mensagem
            LaunchedEffect(chatMessages.size) {
                if (chatMessages.isNotEmpty()) {
                    listState.animateScrollToItem(chatMessages.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(chatMessages) { ChatBubble(it, currentTeacher) }
            }

            Surface(
                shadowElevation = 4.dp,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val lastTeacherMessage = chatMessages.lastOrNull { !it.isUser }
                    if (lastTeacherMessage != null && !isListening) {
                        Button(
                            onClick = { voiceManager.speak(lastTeacherMessage.text, currentTeacher.locale, currentTeacher.gender) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(bottom = 4.dp).height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Ouvir novamente",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Ouvir Pronúncia", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    Text(
                        text = if (isListening) {
                            if (partialTranscript.isNotBlank()) partialTranscript else "Ouvindo..."
                        } else {
                            "Toque no microfone para falar"
                        },
                        fontSize = 12.sp,
                        color = if (isListening) Color.Red else Color.Gray,
                        fontWeight = if (isListening) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Mensagem...", color = Color.Gray) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 64.dp, max = 150.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF5F5F5),
                                unfocusedContainerColor = Color(0xFFF5F5F5),
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 18.sp,
                                lineHeight = 22.sp
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val t = inputText
                                    chatMessages.add(Message(t, true))
                                    inputText = ""
                                    processLearningMachineResponse(context, t, userSession, currentTeacher, chatMessages, voiceManager, geminiService, scope, listState)
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (inputText.isNotBlank()) Color(0xFF3B71CA) else Color.LightGray)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (isListening) {
                                    speechRecognizer.stopListening()
                                    isListening = false
                                } else {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        isListening = true
                                        speechRecognizer.cancel()
                                        speechRecognizer.startListening(recognitionIntent)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isListening) Color.Red else Color(0xFF1A237E))
                        ) {
                            Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message, teacher: Teacher) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isUser) Color(0xFF3B71CA) else Color.White
    val textColor = if (message.isUser) Color.White else Color.Black

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = alignment) {
        if (!message.isUser) {
            val title = if (teacher.gender == "F") "Professora" else "Professor"
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    if (!message.isUser) {
                        Text("💬 ", fontSize = 16.sp)
                    }
                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!message.isUser && !message.translation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message.translation,
                        color = Color.Black,
                        fontSize = 15.sp
                    )
                }

                if (!message.isUser && (!message.targetPhrase.isNullOrBlank() || !message.phonetics.isNullOrBlank())) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🗣️ ", fontSize = 16.sp)
                        Column {
                            if (!message.targetPhrase.isNullOrBlank()) {
                                Text(
                                    text = "*${message.targetPhrase}*",
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (!message.phonetics.isNullOrBlank()) {
                                Text(
                                    text = if (message.targetPhrase.isNullOrBlank()) message.phonetics else "(${message.phonetics})",
                                    color = Color.Black,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                if (!message.isUser && !message.vocabulary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📚 ", fontSize = 16.sp)
                        Text(
                            text = "*${message.vocabulary}*",
                            color = Color.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

fun processLearningMachineResponse(
    context: Context,
    userText: String,
    session: UserSessionState,
    teacher: Teacher,
    messages: MutableList<Message>,
    voiceManager: VoiceManager,
    geminiService: GeminiService,
    scope: kotlinx.coroutines.CoroutineScope,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    scope.launch {
        delay(800)
        session.interactionCount++

        if (session.waitingForPronunciation && session.expectedPhrase != null) {
            val similarity = calculateSimilarity(userText, session.expectedPhrase!!)
            if (similarity > 0.7) {
                session.waitingForPronunciation = false
                session.retryCount = 0
                session.markLearned(session.expectedPhrase!!)
                session.saveProgress()
                session.sessionGrade = 100

                val (okMsg, okTrans) = when(session.language) {
                    "Espanhol" -> "¡Perfecto! Tu pronunciación es excelente. Vamos a continuar." to "Perfeito! Sua pronúncia é excelente. Vamos continuar."
                    "Chinês" -> "非常好！你的发音很完美。我们继续吧。" to "Muito bem! Sua pronúncia está perfeita. Vamos continuar."
                    "Japonês" -> "素晴らしい！完璧な発音です. 続けましょう。" to "Incrível! Pronúncia perfeita. Vamos continuar."
                    "Alemão" -> "Perfekt! Deine Aussprache ist hervorragend. Machen wir weiter." to "Perfeito! Sua pronúncia é excelente. Vamos continuar."
                    "Italiano" -> "Perfetto! La tua pronuncia è eccellente. Continuiamo." to "Perfeito! Sua pronúncia é excelente. Vamos continuar."
                    "Francês" -> "Parfait ! Ta prononciation est excellente. Continuons." to "Perfeito! Sua pronúncia é excelente. Vamos continuar."
                    "Russo" -> "Отлично! Твое произношение превосходно. Давай продолжим." to "Excelente! Sua pronúncia está soberba. Vamos continuar."
                    else -> "Perfect pronunciation! Let's learn something new." to "Pronúncia perfeita! Vamos aprender algo novo."
                }

                messages.add(Message(okMsg, false, translation = okTrans))
                voiceManager.speak(okMsg, teacher.locale, teacher.gender)
                session.expectedPhrase = null
            } else {
                session.retryCount++
                if (session.retryCount >= 3) {
                    session.waitingForPronunciation = false
                    session.retryCount = 0
                    session.expectedPhrase = null

                    val (skipMsg, skipTrans) = when(session.language) {
                        "Espanhol" -> "No hay problema, vamos a seguir adelante. Practicaremos esto mais tarde." to "Sem problemas, vamos seguir em frente. Praticaremos isso mais tarde."
                        "Chinês" -> "没关系，我们继续吧. 我们以后再练习 esse." to "Sem problemas, vamos seguir em frente. Praticaremos isso mais tarde."
                        "Japonês" -> "問題ありません. 次へ進みましょう. また後で练习しましょう." to "Sem problemas, vamos seguir em frente. Praticaremos isso mais tarde."
                        "Alemão" -> "Kein Problem, machen wir weiter. Wir üben das später noch einmal." to "Sem problemas, vamos seguir em frente. Praticaremos isso mais tarde."
                        "Italiano" -> "Nessun problema, andiamo avanti. Lo faremo di novo mais tarde." to "Sem problemas, vamos seguir em frente. Praticaremos isso mais tarde."
                        "Francês" -> "Pas de problème, on continue. On s'entraînera plus tard." to "Sem problemas, vamos seguir em frente. Praticaremos isso mais tarde."
                        "Russo" -> "Ничего страшного, давай продолжим. Мы попрактикуемся в этом позже." to "Sem problemas, vamos seguir em frente. Praticaremos isso mais tarde."
                        else -> "No problem, let's move on. We'll practice this more later." to "Sem problemas, vamos seguir em frente. Praticaremos isso mais tarde."
                    }
                    messages.add(Message(skipMsg, false, translation = skipTrans))
                    voiceManager.speak(skipMsg, teacher.locale, teacher.gender)
                } else {
                    val (retryMsg, retryTrans) = when(session.language) {
                        "Espanhol" -> "No está del todo correto. Escucha con atenção: '${session.expectedPhrase}'. Repítelo, por favor." to "Não está totalmente correto. Ouça com atenção e repita, por favor."
                        "Chinês" -> "不完全正确. 仔细聽：'${session.expectedPhrase}'。请再试一次。" to "Não está totalmente correto. Ouça com atenção e tente novamente."
                        "Japonês" -> "正しくありません. よく聞いてください：'${session.expectedPhrase}'。もう一度言ってください。" to "Não está correto. Ouça com atenção e diga novamente."
                        "Alemão" -> "Das ist nicht ganz richtig. Hör gut zu: '${session.expectedPhrase}'. Bitte wiederhole es." to "Não está totalmente correto. Ouça com atenção e repita."
                        "Italiano" -> "Non è del tutto corretto. Ascolta bene: '${session.expectedPhrase}'. Per favore, ripeti." to "Não está totalmente correto. Ouça com atenção e repita."
                        "Francês" -> "Ce n'est pas tout à fait exact. Écoute bien : '${session.expectedPhrase}'. Répète, s'il te plaît." to "Não está totalmente correto. Ouça com atenção e repita."
                        "Russo" -> "Это не совсем верно. Послушай внимательно: '${session.expectedPhrase}'. Повтори, пожалуйста." to "Não está totalmente correto. Ouça com atenção e repita."
                        else -> "Not quite correct. Listen carefully: '${session.expectedPhrase}'. Please repeat it again." to "Não está totalmente correto. Ouça com atenção e repita novamente."
                    }

                    messages.add(Message(retryMsg, false, translation = retryTrans))
                    voiceManager.speak(retryMsg, teacher.locale, teacher.gender)
                    return@launch
                }
            }
        }

        try {
            val nextStep = geminiService.getNextLessonStep(session, userText, teacher)

            session.expectedPhrase = nextStep.targetPhrase
            session.waitingForPronunciation = nextStep.requiresPronunciation
            session.markAsked(nextStep.text)

            session.saveProgress()
            session.sessionGrade = nextStep.sessionGrade

            if (!nextStep.suggestedLevel.isNullOrBlank() && nextStep.suggestedLevel != session.level) {
                if (nextStep.sessionGrade >= 80) {
                    Toast.makeText(context, "Parabéns! Você atingiu ${nextStep.sessionGrade}% e o professor sugere que você suba para o nível ${nextStep.suggestedLevel}!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "O professor sugere o nível ${nextStep.suggestedLevel}, mas você precisa de 80% de acerto (atual: ${nextStep.sessionGrade}%). Vamos praticar mais!", Toast.LENGTH_LONG).show()
                }
            }

            messages.add(
                Message(
                    text = nextStep.text,
                    isUser = false,
                    translation = nextStep.translation,
                    phonetics = nextStep.phonetics,
                    vocabulary = nextStep.vocabulary,
                    targetPhrase = nextStep.targetPhrase
                )
            )
            voiceManager.speak(nextStep.text, teacher.locale, teacher.gender)

        } catch (e: Exception) {
            val (errStr, errTrans) = when(session.language) {
                "Espanhol" -> "Lo siento, tuve um problema. Inténtalo de nuevo." to "Desculpe, tive um problema. Tente novamente."
                "Chinês" -> "抱歉，系统出现问题。请再试一次。" to "Desculpe, o sistema teve um problema. Tente novamente."
                "Japonês" -> "すみません、問題が発生しました。もう一度お試しください." to "Desculpe, ocorreu um problema. Tente novamente."
                "Alemão" -> "Entschuldigung, ich hatte ein Problem. Bitte versuche es erneut." to "Desculpe, tive um problema. Tente novamente."
                "Italiano" -> "Spiacente, ho avuto um problema. Per favore riprova." to "Desculpe, tive um problema. Tente novamente."
                "Francês" -> "Désolé, j'ai eu um problema. Veuillez réessayer." to "Desculpe, tive um problema. Tente novamente."
                "Russo" -> "Извините, возникла проблема. Пожалуйста, попробуйте еще раз." to "Desculpe, ocorreu um problema. Tente novamente."
                else -> "Sorry, I had a problem processing your request. Please try again." to "Desculpe, tive um problema ao processar seu pedido. Tente novamente."
            }
            messages.add(Message(errStr, false, translation = errTrans))
            voiceManager.speak(errStr, teacher.locale, teacher.gender)
        }
    }
}

fun calculateSimilarity(s1: String, s2: String): Double {
    fun normalize(s: String): String {
        return s.lowercase()
            // Remove pontuação e caracteres especiais, mas mantém letras de qualquer idioma e números
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    val str1 = normalize(s1)
    val str2 = normalize(s2)

    if (str1 == str2) return 1.0
    if (str1.isEmpty() || str2.isEmpty()) return 0.0

    if (str1.length <= 3 || str2.length <= 3) {
        var matches = 0
        val minLen = minOf(str1.length, str2.length)
        for (i in 0 until minLen) {
            if (str1[i] == str2[i]) matches++
        }
        return matches.toDouble() / maxOf(str1.length, str2.length)
    }

    val words1 = str1.split(" ").toSet()
    val words2 = str2.split(" ").toSet()
    val intersection = words1.intersect(words2).size
    val union = words1.union(words2).size
    return intersection.toDouble() / union.toDouble()
}

data class LessonStep(
    val id: String,
    val text: String,
    val translation: String,
    val targetPhrase: String? = null,
    val requiresPronunciation: Boolean = false,
    val phonetics: String? = null,
    val vocabulary: String? = null,
    val awardedXp: Int = 10,
    val sessionGrade: Int = 0,
    val suggestedLevel: String? = null
)