# Operating Architecture: Antigravity & Argon 4

## 1. Role Division & Responsibilities
- **Antigravity (This Agent)**:
  - Single agent for ALL code, logic, bug fixes, refactoring, lifecycle, audio DSP, system reflection, Gradle, ADB, and device testing.
  - Owns all wiring between stateless UI components and backend/system logic.
  - Full repository context: reads all files and guards shared state against regressions.
  - Directly runs builds, tests on device via ADB, commits and pushes.
- **Gemini Argon 4 (Google Chat)**:
  - Used EXCLUSIVELY for visual art, UI design, layout XML/Composables, colors, spacing, typography, and vector assets.
  - Design work comes from Argon 4 as strictly STATELESS UI pieces (receives data properties, emits callback lambdas).
  - NEVER write business logic, state management, lifecycle, audio, or services in Argon 4 prompts.

---

## 2. Mandatory Rules Before Touching Code
1. **Pre-Flight Context**: Before reading or editing anything, read [`BUGS.md`](./BUGS.md), [`ARCHITECTURE.md`](./ARCHITECTURE.md), and [`DESIGN.md`](./DESIGN.md).
2. **Invariant Protection**: Check `BUGS.md` "Must-not-break" fields for any code touching the shared state map.
3. **Design Handoff**: When visual design is requested, Antigravity prepares a pure copy-paste prompt for Argon 4 with metadata placed outside the code block. When the user brings the design back, Antigravity wires it to the logic and runs verification on device.
4. **One Bug Per Commit**: Commit each fix atomically with its Bug ID.
5. **No Weakening Tests**: Never delete or weaken a test or harness check to make a fix or design fit.

