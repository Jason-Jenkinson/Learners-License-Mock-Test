# Learners License Mock Test

## Run locally

The application has a Spring Boot API backed by MySQL and a vanilla HTML/CSS/JS
frontend. Create a MySQL database named `mocktest` and a database user with
permissions to create and update tables. The backend reads its connection
settings from environment variables; do not commit database credentials.
The backend requires JDK 17 or newer; set `JAVA_HOME` to the JDK install folder
if Java is not already configured in your environment.

In PowerShell, set the connection settings and start the backend:

```powershell
$env:DB_URL = "jdbc:mysql://127.0.0.1:3306/mocktest"
$env:DB_USERNAME = "<your MySQL username>"
$env:DB_PASSWORD = "<your MySQL password>"
.\Backend\mvnw.cmd -f Backend\pom.xml spring-boot:run
```

If MySQL is not installed as a Windows service, start the local server in a
separate PowerShell window and leave it open:

```powershell
$mysqlHome = "C:\Program Files\MySQL\MySQL Server 8.0"
$dataDir = Join-Path $env:LOCALAPPDATA "MySQL\LearnersLicenseMockTest\Data"
& "$mysqlHome\bin\mysqld.exe" --console "--basedir=$mysqlHome" "--datadir=$dataDir" --bind-address=127.0.0.1 --port=3306 --mysqlx=0
```

The local instance binds only to this computer and keeps its data under the
current Windows user's local application data. Open a new PowerShell window
after setting the `DB_*` environment variables so the backend inherits them.

In VS Code, serve `Views/start-test.html` with Live Server. The frontend calls
the API on port `8080` using the same hostname as the frontend, so use
`localhost` or `127.0.0.1` consistently for both.

The default CORS origin patterns allow local Live Server instances on
`localhost` and `127.0.0.1`. Set `CORS_ALLOWED_ORIGIN_PATTERNS` to the required
comma-separated origin patterns for another development or deployment host.

## Testing

Run `.\mvnw.cmd test` from the `Backend` folder. See
[Backend/TESTING.md](Backend/TESTING.md) for what the tests cover and how to add
new ones.

## Learner features

- Guest learners can take a test without registering. Registered learners can
  log in, complete tests, and view their saved attempt history. Guests can
  choose to save a completed result by logging in or registering from the
  results page.
- The target test has 64 questions across Rules of the Road, Road Signs, and
  Controls of the Vehicle. Only verified and active questions are selected.
- The current backend seed contains one sample question per category, so tests
  contain the eligible questions currently available (three until the bank is
  reviewed and populated). The interface displays the server's actual count.
- Answers are saved to the backend as they are selected. The frontend provides
  a 20-minute timer and submits when the learner finishes or time expires.
- The backend calculates the total score and pass/fail for each category. Its
  current category pass marks are 22/28 for Rules of the Road, 23/28 for Road
  Signs, and 6/8 for Controls of the Vehicle; required marks scale to the number
  of eligible questions when the question pool is smaller than the target.
- Results show the overall score and pass status, completion time, category
  breakdown, and incorrect or unanswered answers with the correct answer and
  explanation when available.

The frontend stores the active attempt ID, selected answers, and timer state in
the current browser tab's session storage. The backend remains authoritative for
saved answers, scores, and registered learners' history.

The admin question-management endpoints exist in the backend; an admin
management UI is not included in this frontend scope.

## Design

The design reference is
[Natis Score screen in Figma](https://www.figma.com/design/fKPWDfH4ofFbKqKBvoxUwV/Natis-Score-screen?node-id=0-1&t=LKIVs8GWYWHGV2Hv-1).
