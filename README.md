# Dailer — AI Voice Clone Detection for Phone Calls

Dailer protects elderly parents from AI voice-cloning scams by intercepting incoming calls on the parent's Android phone, buffering the opening 0–120s (up to 140s) of audio locally, and running it through two verification checks on a private backend:

1. **Aletheia** — Synthetic/AI-generated speech detection ("Is this real human speech or AI-cloned?").
2. **Eidos** — Speaker verification ("Does this match the enrolled relative's voiceprint?").

---

## Project Structure

```text
DAILER/
├── android/                  # Kotlin Android app (ROLE_DIALER, Room, WorkManager, Material 3)
│   ├── app/build.gradle.kts
│   └── app/src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/dailer/app/
│       │   ├── data/         # Room entities/DAOs, Retrofit API & DTOs, Repository
│       │   ├── service/      # CallScreeningService, InCallService, CallRecordingService, UploadWorker
│       │   ├── ui/           # Setup, QR Generator, Enrollment, Relatives, Dialer, In-Call & Banner
│       │   └── util/         # RoleManager, Permissions, Crypto, Audio, Constants
│       └── res/              # Layouts, Navigation graph, Themes, Drawables
└── backend/                  # Python Flask API server
    ├── app.py                # Flask app factory
    ├── config.py             # Environment-based configuration
    ├── routes/               # /api/health, /api/enroll, /api/verify, /api/enrollment/<id>
    ├── services/             # Aletheia, Eidos, Encrypted Storage, Verdict combiner
    ├── models/               # Pydantic request/response schemas
    ├── utils/                # Fernet crypto & WAV/audio validation
    ├── tests/                # API integration tests
    ├── Dockerfile
    └── docker-compose.yml
```

---

## Quick Start

### 1. Backend Setup (Python 3.11+)

```bash
cd backend
python -m venv .venv
# Windows: .venv\Scripts\activate
# macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
python run.py
```

Or with Docker:

```bash
cd backend
cp .env.example .env
docker compose up --build
```

The API listens on `http://0.0.0.0:5000` by default (the Android emulator reaches it at `http://10.0.2.2:5000`).

#### Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/health` | Health check, uptime, active enrollment count |
| `POST` | `/api/enroll` | Enroll a relative's 20–30s calibration audio (`name`, `phone_number`, `relationship`, `audio_base64`) |
| `GET` | `/api/enrollments` | List enrolled relatives (metadata only, no raw embeddings) |
| `DELETE` | `/api/enrollment/<id>` | Revoke and delete a relative's voiceprint |
| `POST` | `/api/verify` | Run Aletheia + Eidos in parallel on call audio and return trust verdict |

> **Note:** In MVP mode (when `ALETHEIA_API_URL` / `EIDOS_API_URL` are unset), `AletheiaService` and `EidosService` run in placeholder mode. Set the provider URLs and API keys in `backend/.env` to connect live models. API keys never leave the backend.

### 2. Android App Setup

1. Open `android/` in **Android Studio** (JDK 17, Gradle 8.5, `minSdk = 26`, `targetSdk = 34`).
2. Sync Gradle and run on an Android device or emulator.
3. On the **Setup** screen (for the installer):
   - Grant required runtime permissions (`RECORD_AUDIO`, `READ_PHONE_STATE`, `READ_CALL_LOG`, `POST_NOTIFICATIONS`, etc.).
   - Set **Dailer** as the default dialer (`RoleManager.ROLE_DIALER`).
   - Confirm the backend URL (`http://10.0.2.2:5000` for emulator, or your LAN/HTTPS server URL for physical devices).
4. Use **Add Relative / QR Generator** to create a `dailer://enroll?...` invite link/QR code and share it with family members.

---

## Platform, Legal & Privacy Notes

- **Android 10+ Call Recording Constraint:** Stock Android restricts third-party call recording. Holding `ROLE_DIALER` (`InCallService` + `CallScreeningService`) is the standard workaround; `AudioRecorder` attempts `MediaRecorder.AudioSource.VOICE_CALL` and falls back to `MIC` if blocked by device/OEM policy.
- **Call-Recording Consent & Telecom Regulations:** Laws vary by jurisdiction (e.g., India generally permits one-party consent, though telecom regulations may still require an in-call disclosure or beep). Verify local compliance before production rollout.
- **Biometric Data & Retention:** Voice embeddings are encrypted at rest with Fernet (`cryptography`), relatives can revoke their enrollment via `DELETE /api/enrollment/<id>`, and temporary call audio clips are automatically purged after the configured retention window (`CALL_AUDIO_RETENTION_HOURS`).

