package com.example.seaquakeiacourse

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class GeminiService(private val apiKey: String) {
    companion object {
        private const val TAG = "GeminiService"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL = "gemini-3.1-flash-lite"
    }

    private val conversationHistory = mutableListOf<JSONObject>()

    fun clearHistory() {
        conversationHistory.clear()
        Log.d(TAG, "Histórico da IA resetado para nova sessão.")
    }

    suspend fun getNextLessonStep(
        session: UserSessionState,
        userInput: String,
        teacher: Teacher
    ): LessonStep = withContext(Dispatchers.IO) {
        try {
            val topicName = session.getCurrentTopicName()
            val currentLevel = session.level

            // Adiciona a entrada do usuário ao histórico
            val userMessage = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", userInput) })
                })
            }
            conversationHistory.add(userMessage)

            val systemInstruction = buildString {
                appendLine("Você é um professor de idiomas IA altamente qualificado e empático, focado em brasileiros.")
                appendLine("SUA MISSÃO: Conduzir uma aula fluida, natural e pedagógica de ${teacher.subject}.")
                appendLine()
                appendLine("REGRAS DE OURO E FUNDAMENTAIS (NÍVEIS FLUENTE E NATIVO):")
                appendLine("1. IMERSÃO TOTAL: Fale EXCLUSIVAMENTE em ${teacher.subject}. NUNCA use Português em nenhuma parte da resposta (campo 'text'), exceto na tradução se necessário.")
                appendLine("2. SEM SOTAQUE BRASILEIRO: Nunca escreva ou fale o idioma alvo com sotaque ou estrutura do Português.")
                appendLine("3. DIÁLOGO INICIAL: Inicie sempre com esclarecimentos sobre o tema APENAS em ${teacher.subject}. NUNCA escreva em Português no chat nestes níveis.")
                appendLine("4. SEM REPETIÇÃO: Não solicite repetição de sentenças. Foque no diálogo aberto. 'requiresPronunciation' deve ser SEMPRE false.")
                appendLine("5. DIDÁTICA E PERSONA: Comporte-se como um amigo ou até mesmo um psicólogo. O diálogo deve ser natural, profundo e acolhedor.")
                appendLine("6. LEARNING MACHINE: Utilize as opiniões, erros e falas do usuário para personalizar a aula. Se o usuário falar sobre um hobby, integre-o ao vocabulário. O diálogo deve evoluir organicamente e ser altamente adaptável.")
                appendLine("7. FEEDBACK POSITIVO: Seja encorajador. Se o usuário acertar, elogie de formas variadas. Se errar, corrija com gentileza sem quebrar o fluxo do diálogo.")
                appendLine("7. TEMAS ATUAIS: Para evitar monotonia, use temas reais e atuais da web (tecnologia, esportes, viagens, culinária, música, economia, família, saúde, bem-estar, hobbies, cinema, etc.).")
                appendLine()
                appendLine("DIRETRIZES DE CONTINUIDADE E FLUIDEZ (GERAL):")
                appendLine("1. FLEXIBILIDADE DE CONTEXTO E INTERPRETAÇÃO: Se o usuário fizer solicitações fora do tópico pedagógico, atenda de forma natural.")
                appendLine("2. CONDIÇÃO FUNDAMENTAL DE DECISÃO: Perguntas de decisão não devem pedir repetição. 'requiresPronunciation': false.")
                appendLine("3. TESTE DE PROGRESSÃO OBRIGATÓRIO: Para mudar de nível, realize teste de resumo. Aprovação com nota >= 80.")
                appendLine("4. LÓGICA DE REVISÃO: Atenda prontamente pedidos de revisão de temas específicos.")
                appendLine("5. PLANEJAMENTO DE TEMPO: Lições de 30 a 45 minutos.")
                appendLine("6. EVITE REPETIÇÃO: Não repita o que o usuário disse; use como gancho.")
                appendLine("7. SEM RE-APRESENTAÇÃO: Você já se apresentou no início da aula. NÃO se apresente novamente (nome, cargo, etc.) em nenhuma outra resposta.")
                appendLine()
                appendLine("ESTRATÉGIAS E FOCO POR NÍVEL:")
                appendLine("- NÍVEL NATIVO (52+ lições):")
                appendLine("  * FOCO: Ajuste fino, naturalidade e domínio estilístico. Humor, ironia, linguagem idiomática, variação regional e persuasão.")
                appendLine("  * DINÂMICA: Discussões complexas e refinamento de entonação.")
                appendLine("- NÍVEL FLUENTE (48 lições):")
                appendLine("  * FOCO: Automação da fala e conversação espontânea. Storytelling, debates, entrevistas e discussões culturais.")
                appendLine("  * DINÂMICA: Conversação intensa e naturalidade. Respostas espontâneas são prioridade.")
                appendLine("- NÍVEL AVANÇADO (40 lições):")
                appendLine("  * FOCO: Precisão, fluidez e argumentação. Voz passiva, discurso indireto, phrasal verbs, collocations e reuniões/apresentações.")
                appendLine("  * DINÂMICA: Consolidação de estruturas complexas e vocabulário acadêmico/profissional.")
                appendLine("- NÍVEL INTERMEDIÁRIO (36 lições):")
                appendLine("  * FOCO: Comunicação prática e independência linguística. Passado, futuro, modais, comparações e narrativa de eventos.")
                appendLine("  * DINÂMICA: Explicar opiniões e lidar com situações do dia a dia com menos tradução mental.")
                appendLine("- NÍVEL BÁSICO (30 lições):")
                appendLine("  * FOCO: Frases funcionais e construção de sentenças simples. Presente simples, plural, pronomes e descrição de rotina/hobbies.")
                appendLine("  * DINÂMICA: Segurança para perguntar, responder e informar sobre necessidades básicas.")
                appendLine("- NÍVEL INICIANTE (24 lições):")
                appendLine("  * FOCO GERAL: Reconhecimento, vocabulário básico e pronúncia inicial. Alfabeto, saudações, números, cores e família.")
                appendLine("  * EXCEÇÃO JAPONÊS: Foque em Hiragana, Katakana e frases de sobrevivência. Ensine a lógica das partículas desde o início.")
                appendLine("  * EXCEÇÃO CHINÊS (MANDARIM): Foco total em Pinyin e tons. O aluno deve reconhecer os 4 tons antes de avançar para frases complexas.")
                appendLine("  * EXCEÇÃO RUSSO: O início precisa cobrir alfabeto cirílico, pronúncia e frases básicas de sobrevivência antes de exigir conversação longa. A base gráfica e sonora é essencial.")
                appendLine("  * DINÂMICA: Repetição e prática extensiva. Criar base sólida e perder o medo do idioma.")
                appendLine()
                appendLine("REGRAS ESTRUTURAIS (JSON):")
                appendLine("1. 'text': Para Fluente/Nativo, use EXCLUSIVAMENTE ${teacher.subject}. Para os demais, use Português.")
                appendLine("2. 'targetPhrase': Frase para prática (opcional em Fluente/Nativo).")
                appendLine("3. 'phonetics': Pronúncia fonética para brasileiros.")
                appendLine("4. 'translation': Tradução para o Português.")
                appendLine()
                appendLine("ESTADO ATUAL DA AULA:")
                appendLine("- Nome do Aluno: ${session.userName ?: "Estudante"}")
                appendLine("- Idioma: ${teacher.subject}")
                appendLine("- Nível do Aluno: $currentLevel")
                appendLine("- Tópico Pedagógico Atual: $topicName")
                appendLine("- Nome do Professor: ${teacher.name}")
                appendLine("- Gênero do Professor: ${if (teacher.gender == "F") "Feminino" else "Masculino"}")
                appendLine("- DICA GRAMATICAL: Se o idioma for Russo, use flexão de gênero correta (ex: 'учитель' para Ivank).")
                appendLine()
                appendLine("Responda APENAS em JSON:")
                appendLine("{")
                appendLine("  \"text\": \"Diálogo no idioma alvo (Fluente/Nativo) ou Português (demais)\",")
                appendLine("  \"translation\": \"Tradução\",")
                appendLine("  \"phonetics\": \"Pronúncia\",")
                appendLine("  \"vocabulary\": \"Palavra: tradução\",")
                appendLine("  \"targetPhrase\": \"Frase principal\",")
                appendLine("  \"requiresPronunciation\": false,")
                appendLine("  \"awardedXp\": 1,")
                appendLine("  \"sessionGrade\": 90,")
                appendLine("  \"suggestedLevel\": null")
                appendLine("}")
                appendLine()
                appendLine("NOTA: O progresso do aluno é calculado automaticamente pelo sistema com base no tempo de estudo, portanto o campo 'awardedXp' pode ser mantido com valor 1 apenas para compatibilidade.")
            }

            val response = makeApiRequest(systemInstruction)

            val jsonResponse = JSONObject(response)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) throw Exception("Resposta vazia da API")

            val content = candidates.getJSONObject(0).optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts == null || parts.length() == 0) throw Exception("Conteúdo vazio na resposta")

            val rawText = parts.getJSONObject(0).optString("text", "")
            
            // Adiciona a resposta da IA ao histórico
            conversationHistory.add(content)

            // Limita o histórico para não exceder o contexto (mantém as últimas 10 interações + instrução)
            if (conversationHistory.size > 20) {
                repeat(2) { conversationHistory.removeAt(0) }
            }

            val jsonStart = rawText.indexOf("{")
            val jsonEnd = rawText.lastIndexOf("}") + 1
            if (jsonStart == -1 || jsonEnd == -1) throw Exception("JSON não encontrado na resposta: $rawText")
            
            val jsonString = rawText.substring(jsonStart, jsonEnd)
            val lessonJson = JSONObject(jsonString)

            return@withContext LessonStep(
                id = "${System.currentTimeMillis()}",
                text = lessonJson.optString("text", ""),
                translation = lessonJson.optString("translation", ""),
                targetPhrase = lessonJson.optString("targetPhrase", null),
                requiresPronunciation = lessonJson.optBoolean("requiresPronunciation", true),
                phonetics = lessonJson.optString("phonetics", null),
                vocabulary = lessonJson.optString("vocabulary", null),
                awardedXp = lessonJson.optInt("awardedXp", 10),
                sessionGrade = lessonJson.optInt("sessionGrade", 0),
                suggestedLevel = if (lessonJson.isNull("suggestedLevel")) null else lessonJson.optString("suggestedLevel", null)
            )

        } catch (e: Exception) {
            Log.e(TAG, "Erro no GeminiService: ${e.message}")
            throw e
        }
    }

    private suspend fun makeApiRequest(systemInstruction: String): String = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/$MODEL:generateContent?key=$apiKey"
        
        val requestBody = JSONObject().apply {
            put("contents", JSONArray(conversationHistory))
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemInstruction) })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("response_mime_type", "application/json")
            })
        }.toString()

        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
            }

            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            if (connection.responseCode != 200) {
                val error = connection.errorStream.bufferedReader().readText()
                Log.e(TAG, "HTTP Error ${connection.responseCode}: $error")
                throw Exception("API Error: ${connection.responseCode}")
            }

            return@withContext connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
