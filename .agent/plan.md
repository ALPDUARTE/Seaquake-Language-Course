# Project Plan

An Android app called "Seaquake IA Course" to teach English and Mandarin using AI agents. The agents converse with users in the chosen language and level. The app features a welcome screen with a virtual secretary welcoming the user.

## Project Brief

# Seaquake IA Course - Project Brief

Seaquake IA Course is an innovative AI-driven language learning platform designed to teach English and Mandarin through immersive conversation. The app leverages adaptive layouts and modern Material Design 3 to provide an energetic and professional learning environment.

## Features

*   **Virtual Secretary Welcome:** An immersive onboarding experience featuring an AI-driven virtual secretary that greets the user and introduces the course curriculum.
*   **AI Conversation Agents:** Interactive chat interface where users engage in real-time dialogue with AI agents in their target language (English or Mandarin).
*   **Adaptive Language & Level Selection:** A personalized setup flow that allows users to select their target language and current proficiency level, tailoring the AI interactions to their needs.
*   **Multi-Pane Learning Interface:** Utilizing adaptive layouts to provide a seamless transition between the chat interface and learning resources (like vocabulary or grammar tips) on larger screens.

## High-Level Tech Stack

*   **Kotlin:** The primary programming language for robust and expressive app development.
*   **Jetpack Compose (Material 3):** For building a vibrant, energetic, and edge-to-edge UI following the latest Material Design guidelines.
*   **Jetpack Navigation 3:** A state-driven navigation framework to manage the flow between the welcome screen and learning modules.
*   **Compose Material Adaptive:** Used to ensure the UI scales beautifully across different form factors, such as phones, tablets, and foldables.
*   **Kotlin Coroutines:** For handling asynchronous tasks, such as AI API calls and real-time conversation processing.
*   **Coil:** For efficient loading and caching of virtual agent avatars and UI assets.

## Implementation Steps

### Task_1_Foundation_Theme_Navigation: Configure the Material 3 theme with a vibrant color scheme, enable edge-to-edge display, and set up the Navigation 3 structure for the app.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Project builds successfully
  - Vibrant M3 theme (light/dark) is applied
  - Edge-to-edge display enabled in MainActivity
  - Navigation 3 host is initialized
- **StartTime:** 2026-06-14 12:20:26 BRT

### Task_2_Onboarding_Flow: Implement the Virtual Secretary welcome screen and the language/level selection interface.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Virtual Secretary screen greets the user
  - Language (English/Mandarin) and proficiency level selection functional
  - User preferences are persisted or passed to the next screen
  - Navigation between onboarding and main chat works

### Task_3_AI_Chat_Adaptive_UI: Build the AI conversation interface using adaptive layouts to support both handheld and large screen devices.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Interactive chat UI implemented with Jetpack Compose
  - Adaptive layout used (e.g., List-Detail or multi-pane) for larger screens
  - AI agent avatars load using Coil
  - Mock or actual AI response handling implemented

### Task_4_Assets_Final_Verify: Create an adaptive app icon and perform final UI refinements and stability checks.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Adaptive app icon created matching the app's theme
  - Vibrant and energetic design consistent across all screens
  - App does not crash during navigation or interaction
  - Project builds and runs on a device/emulator

