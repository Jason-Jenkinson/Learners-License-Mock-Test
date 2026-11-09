# Testing the backend

The backend has 94 automated tests. Run them from the `Backend` folder:

```powershell
cd Backend
.\mvnw.cmd test
```

The run takes about 25 seconds and ends with `BUILD SUCCESS`. You don't need MySQL running for it, or the `DB_*` environment variables set. If one test fails, Maven prints its name and the line where it failed. A full report for each test class is saved in `target/surefire-reports/`.

To run one test class, or one test inside it:

```powershell
.\mvnw.cmd test -Dtest=TestServiceTest
.\mvnw.cmd test -Dtest=TestServiceTest#oneBelowPassMarkFails
```

## Two kinds of tests

**Unit tests (90 tests).** These check one class at a time. They use Mockito to replace the repositories with fakes, so no database or Spring context is involved. The 90 count includes `PasswordHasherTest`, which doesn't need any fakes. Each test sets up the exact data it needs, which makes it easy to test edge cases such as a learner scoring one mark below the pass mark. The unit tests live under `src/test/java/com/mocktest/` in the `service`, `controller` and `config` folders, which mirror the main code.

**Integration tests (4 tests).** These are `BackendApplicationTests` and `QuestionBankFlowTests`. They start the whole Spring application against an in-memory H2 database and import the real question bank from `Assets/extracted_pdf_questions.json`. They're slower, about 15 seconds of the run, but they show that the pieces work together: the bank imports into all three categories, a full test has 28 + 28 + 8 questions, all correct answers pass, and a blank test fails.

The integration tests use the settings in `src/test/resources/application-test.properties`, which `@ActiveProfiles("test")` switches on. H2 runs in MySQL compatibility mode and the database is thrown away after each run.

## What each unit test file covers

| File | What it checks |
|---|---|
| `service/TestServiceTest` | The scoring rules. A learner passes exactly at 22/28, 23/28 and 6/8 and fails one mark below. Failing any one category fails the whole test, even with 61/64 overall. When a category has fewer questions than usual, the pass mark scales down (10 rules questions need 8 correct). Wrong and skipped answers are listed with the right answer. It also checks how questions are picked when a test starts, answers that don't fit (an option from a different question, answering after submitting), and saving a guest's result to an account. |
| `service/QuestionServiceTest` | Adding questions through the admin API: text and source are required, there must be at least two options and exactly one correct. A duplicate of an active question is refused. A retired question doesn't count as a duplicate, and neither does the question being corrected. Also covers changing status, retiring and correcting. |
| `service/PasswordHasherTest` | A correct password verifies and a wrong one doesn't. Hashing the same password twice gives two different results, because of the salt. |
| `controller/AuthControllerTest` | Register and login checks: usernames need 3 characters and passwords 6, a username can't be taken twice, and passwords are stored hashed. A wrong password and an unknown user get the same 401 response. Also covers logout and `/me`. |
| `controller/TestControllerTest` | Who can touch which attempt. A guest can only use attempts started in their own browser session. A logged-in user can't read or change someone else's. Saving a result and viewing history both require a login. |
| `controller/AdminControllerTest` | Every admin endpoint refuses a missing or wrong `X-Admin-Key` before doing anything, and works with the right key. |
| `config/QuestionBankImporterTest` | Importing the JSON bank, using small JSON files written during the test. Valid questions come in as Verified with the right option marked. Entries with no text, an unknown category, fewer than two options or an answer letter that doesn't match an option are skipped. Duplicates are caught regardless of case or spacing, but the same text with a different sign image is kept. Running the import twice doesn't add anything new. |

## Known gaps

MySQL itself is never tested, only H2 pretending to be MySQL. The two are close but not identical; text comparison, column sizes and table creation can behave differently. Before a release, start the app against a real MySQL database (see the main README) and check that the log shows `Question bank import ... 1109 added, 2 skipped`. To test this automatically, Testcontainers can start a real MySQL in Docker during the test run. That would need Docker Desktop on every machine that runs the tests.

The bulk import endpoint (`POST /api/admin/questions/bulk`) was added after these tests were written and has no tests of its own yet.

The frontend has no automated tests.

## Adding a test

Put it next to the existing tests for the same class and copy their setup. Most test files have a small `statusOf(...)` helper that runs a call, expects a `ResponseStatusException`, and returns the HTTP status, so checking an error looks like this:

```java
assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.submit(1L)));
```

Mockito runs in strict mode. If you set up a fake return value that the test never uses, the test fails with `UnnecessaryStubbingException`. Remove the unused setup, or wrap it in `lenient()` when it's shared setup that only some tests use.

If you change the pass marks in `TestService.RULES`, the tests in `TestServiceTest` that use 22/28, 23/28 and 6/8 will fail. Update the numbers there to match.
