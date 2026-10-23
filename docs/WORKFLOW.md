# Dual-Tier AI Pair Programming Workflow & Regression Harness

## 1. Operating Model & Role Division
This workspace follows an evolved, regression-safe two-tier AI architecture:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Antigravity (This System Agent)                     │
│  - System Architect, Environment Debugger & OS Integration Lead        │
│  - Full Repository Context: Reads all files, monitors shared state     │
│  - Maintains BUGS.md and enforces the Regression Harness               │
│  - Manages Gradle, ADB, Physical Devices, HyperOS Tweaks & Builds      │
│  - Prepares cohesive, invariant-guarded prompts for coupled bugs       │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ Formulates cohesive prompts + invariants
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   Target AI Model (User's Secondary)                   │
│                                                                        │
│  • Gemini Argon 4 (Google Chat) - Code logic, coupled bug resolution   │
│  • Gemini 3.1 Pro - Complex architectural design, vector graphics/SVG  │
│                                                                        │
│  OUTPUT PROTOCOL:                                                      │
│  - Isolated Changes: Surgical targeted method replacements             │
│  - Coupled Bugs / Shared State: Full-function or cohesive block diffs  │
│    verified against the Invariants Checklist in BUGS.md                │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ User returns code edits
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│               Antigravity Ingestion & Regression Harness               │
│  - Applies code to workspace files                                     │
│  - Runs `./gradlew assembleDebug`                                      │
│  - Deploys to physical device via ADB                                  │
│  - Executes mandatory 5-step Regression Checklist                      │
│  - Verifies zero crashes in Logcat before committing to Git            │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Model Routing & Context Matrix

| Task Category | Recommended Model | Context Scope | Thinking Budget |
| :--- | :--- | :--- | :--- |
| **Coupled Bugs & Shared State** | **Gemini Argon 4** | **Full Connected Files + BUGS.md** | **Medium / High** |
| **Simple Isolated Bug Fixes** | **Gemini Argon 4** | Targeted Function Snippets | **Medium** |
| **Visual Art, Vector & UI Design** | **Gemini 3.1 Pro** | Standalone Component / SVG | **High** |
| **System Diagnostics & Harness** | **Antigravity** (Local) | Entire Repository | *Native* |

---

## 3. The Anti-Looping Rules

1. **Never Fix Coupled Bugs in Isolation**:
   When Bug A and Bug B share state (e.g. `HyperOSHelper` settings tables + `AudioEffectCenter` reflection + `MainActivity` lifecycle), they must be addressed together in a single prompt with all touched files present.
2. **Invariants Must Be Stated in Every Bug Prompt**:
   Every prompt addressing coupled behavior must paste the **Invariants Checklist** from `BUGS.md`. The model must explicitly confirm that the proposed changes uphold all invariants.
3. **Mandatory Pre-Commit Regression Execution**:
   Before Antigravity commits any fix to Git:
   - Check Logcat for `FATAL EXCEPTION` and `SecurityException`.
   - Verify `Settings.Global` and `Settings.System` via ADB.
   - Verify both Bluetooth connected and disconnected states.
4. **Metadata Placement**:
   Place reference metadata (`Recommended Model`, `Thinking Budget`, `Project`, `Target Environment`) outside the copyable box as bullet points.
