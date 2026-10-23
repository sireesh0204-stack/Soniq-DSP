# Dual-Tier AI Pair Programming Workflow

## 1. Operating Model & Role Division
This workspace follows a strict, high-efficiency two-tier AI architecture:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Antigravity (This System Agent)                     │
│  - System Architect, Environment Debugger & OS Integration Lead        │
│  - Manages Gradle, ADB, Physical Devices, USB Debugging, HyperOS Tweaks │
│  - Directly applies code edits, builds APKs, and verifies live on device│
│  - Prepares copy-paste prompts with model routing & affected files     │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ Formulates targeted prompts
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   Target AI Model (User's Secondary)                   │
│                                                                        │
│  • Gemini Argon 4 (Google Chat) - Code logic, refactors, framework fix │
│  • Gemini 3.1 Pro - Complex architectural design, vector graphics/SVG  │
│                                                                        │
│  OUTPUT PROTOCOL:                                                      │
│  - Surgical code snippets only (e.g., "Replace function X with:")      │
│  - Zero full-file dumps to conserve context and reduce latency         │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ User returns code snippets
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                  Antigravity Ingestion & Live Build                    │
│  - Ingests code, updates target files, compiles with Gradle,           │
│    installs to Android device via ADB, commits and pushes to GitHub.   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Model Routing Matrix

| Task Category | Recommended Model | Thinking Budget | Purpose / Strength |
| :--- | :--- | :--- | :--- |
| **Android Framework & Logic Fixes** | **Gemini Argon 4** (Google Chat) | **Medium** | Fast, accurate Kotlin/Java methods, state management, audio HAL callbacks. |
| **Bug Fixing & Targeted Refactors** | **Gemini Argon 4** (Google Chat) | **Medium / Low** | Surgical snippet replacements without regenerating unaffected boilerplate. |
| **Visual Art, Vector & UI Design** | **Gemini 3.1 Pro** | **High** | High-precision SVG generation, vector math, aesthetic layout design. |
| **System Diagnostics & OS Settings** | **Antigravity** (Local Agent) | *Native* | ADB commands, HyperOS reflection, Android Studio, Gradle wrapper cache. |

---

## 3. Snippet-Only & Prompt Formatting Rules
When Antigravity prepares prompts for the external model (Argon 4 / Pro):
1. **Metadata Outside the Copy Box**:
   - `Recommended Model`, `Thinking Budget`, `Project Name`, and `Target Environment` are displayed **outside** the code block as clean bullet points for the user's reference.
   - The copy-paste block contains **ONLY** the direct prompt so the user can copy it in one click without manually removing tags.
2. **Never Request Full Files**:
   - The prompt must explicitly state: *"DO NOT output full files. Output only targeted method replacements labeled 'Replace function X with:'"*.
3. **Laser-Focused File Scope**:
   - Specify exact file paths and function boundaries so the secondary model does not need to analyze or regenerate the entire project codebase.
