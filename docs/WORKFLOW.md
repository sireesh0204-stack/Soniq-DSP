# Project Operating Workflow: Antigravity & Argon 4

## 1. Operating Model & Role Division

This project operates with a single engineering agent supported by an external visual designer:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Antigravity (This System Agent)                     │
│  - THE SINGLE AGENT FOR ALL CODE, LOGIC, ARCHITECTURE & EXECUTION      │
│  - Full repository context: reads all files, models, and shared state  │
│  - Owns all bug fixing, refactoring, lifecycle, and audio algorithms   │
│  - Manages Gradle, ADB, Physical Devices, Logcat, Builds, and Git      │
│  - Wires stateless UI components to real application logic             │
│  - Enforces BUGS.md, ARCHITECTURE.md, and DESIGN.md invariants         │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ Requests UI/visual layouts
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   Gemini Argon 4 (Google Chat)                         │
│  - VISUAL DESIGN ONLY                                                  │
│  - Layouts (XML / Stateless Composables), Colors, Spacing, Typography   │
│  - Icons, Vector Drawables (<vector>), SVGs, Brand Emblems             │
│  - NEVER writes business logic, state, lifecycle, audio, or services   │
│  - All UI generated is strictly STATELESS (receives data, emits events)│
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Model Routing Matrix

| Task Category | Owner / Model | Context Scope | Execution Mode |
| :--- | :--- | :--- | :--- |
| **All App Logic & Bug Fixing** | **Antigravity** (Local Agent) | Entire Repository | Direct file edits, build, ADB test |
| **Audio DSP & Framework Reflection**| **Antigravity** (Local Agent) | Entire Repository | Direct verification against Logcat |
| **Gradle, ADB & Device Automation** | **Antigravity** (Local Agent) | Local CLI / Shell | Direct build and regression runs |
| **Visual Art, Vector & UI Design** | **Gemini Argon 4** (Google Chat)| Stateless Design Spec | Formulates prompt $\rightarrow$ User brings design back |

---

## 3. Design Handoff Protocol

When visual design changes, new screens, or styling refactors are requested:

### Step 1: Antigravity Prepares the Prompt for Argon 4
Antigravity crafts a ready-to-paste prompt following these formatting rules:
- **Metadata Outside the Code Block**: `Model`, `Project`, and `Target Environment` are displayed outside the code block as bullet points.
- **Pure Code Block**: The code block contains **ONLY** the prompt to be pasted directly into Google Chat.
- **Prompt Contents**:
  1. The screen/component's purpose.
  2. The relevant **`DESIGN.md` design tokens** (pasted inline so the user never has to attach files).
  3. The exact **stateless data fields** the UI receives and the **event callbacks** it must emit (e.g. `onProfileSelected(profile)`, `onMasterToggleClicked()`).
  4. Explicit screen size constraints (e.g. 1220x2712 AMOLED portrait).
  5. The mandatory designer constraint:
     > *"Design only. Do not write business logic, state management, lifecycle, or audio code. The UI must be stateless: it receives state as parameters and reports user actions through callback lambdas. Output one screen or component at a time. Do not output full files unless it is a new standalone component; otherwise output targeted replacements labeled 'Replace function X with:'."*

### Step 2: User Brings the Design Back
When the user pastes Argon 4's visual code into the chat:
1. **Placement**: Antigravity places the layout/vector XML or stateless component into the correct project file.
2. **Wiring**: Antigravity connects the view IDs / lambdas to the underlying business logic, ViewModels, and services.
3. **Verification**: Antigravity compiles the build (`./gradlew assembleDebug`), executes the regression checks, and verifies behavior on the physical device via ADB.
4. **Invariant Protection**: If the design change breaks any existing behavior or test, Antigravity resolves the conflict before declaring completion. Antigravity **never** removes or weakens a test to make a design fit.

---

## 4. Engineering Rules for Antigravity

1. **Full Context**: Antigravity reads and understands the full system state across [`ARCHITECTURE.md`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/ARCHITECTURE.md), [`BUGS.md`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/BUGS.md), and [`DESIGN.md`](file:///d:/Mobile%20Projects/Buds2Pro-HyperOS-Optimizer/DESIGN.md) before modifying files.
2. **Zero Snippet Restrictions on Logic**: Antigravity operates directly across all necessary files without arbitrary token-saving snippet limits that cause regression loops.
3. **One Bug Per Commit**: Every bug fix is committed with its corresponding Bug ID (e.g. `fix(dsp): resolve BUG-002 Spatializer crash`).
