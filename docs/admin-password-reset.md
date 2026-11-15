# Reset the existing administrator password locally

Prepared 13 November 2026. The reset has NOT been executed.

Target: existing administrator ID **1**, in the local MySQL database **refuge_animalier**. Only `admin.mot_de_passe` is updated. No account is created or deleted, and its name/email and all animal, medical, announcement and adoption-request records are left alone.

This is a standalone terminal helper: `Backend/tools/ResetAdminPassword.java`. It is not a web endpoint, is not started with the application, and does not change existing authentication code or configuration.

## How it works

1. Require an explicit `--reset-admin-1` argument and a real Java console. No password can be supplied as an argument. Redirected/piped execution and terminals without a Java console are refused.
2. Ask for the non-secret confirmation `RESET 1`.
3. Prompt twice using `Console.readPassword`, with no characters or asterisks echoed. Require matching entries and the existing `PasswordUtils.hacher` rules: at least 12 characters and at most 72 UTF-8 bytes. Spaces are not trimmed.
4. Hash with the application's BCrypt implementation and fresh cost-12 salt. Verify the generated hash locally. Passwords are never written to a file, command line, environment variable, or helper log. Input character arrays are cleared; the existing String-based API necessarily creates a temporary immutable Java String that cannot be reliably erased in place.
5. Use the existing `DatabaseConnection` and its normal private configuration/environment overrides. Validate the selected database name and a direct localhost/loopback MySQL URL. Do not display connection values.
6. Refuse a missing/non-InnoDB `admin` table or visible administrator-table triggers. These checks avoid nontransactional updates and unexpected trigger side effects; trigger metadata is subject to the database user's permissions.
7. Lock only the existing ID 1 row; update only its password column using a prepared statement. Require exactly one affected row, reread the stored hash privately, then commit. Pre-commit failures roll back; an uncertain commit is reported without an automatic retry.

Only the BCrypt hash is sent in SQL. The helper does not print the password, hash, JDBC exception details, or stack traces. MySQL server-level audit/general logging is controlled by MySQL, not by this helper.

## Before running a reset

- Obtain approval for this procedure first. Preparing or compiling the helper does not reset anything.
- Keep MySQL running. Stop the Java backend using Ctrl+C in the terminal that started it, or the IntelliJ Stop button if started there. Do not stop unrelated Java processes.
- Stopping/restarting Java clears the current in-memory administrator sessions. A password change alone would not invalidate an already-issued session in another running Java process.
- Use an ordinary Windows Terminal or PowerShell window on your own computer. Do not enter the new password in chat, an IDE Run configuration, a PowerShell variable, or a recorded/shared terminal session.
- Leave `Backend/database.local.properties` unchanged. Environment overrides, if set, still apply; the helper refuses another database or a nonloopback URL.

## Step 1 - Open the Backend folder

```powershell
cd "C:\Users\AMIN\Desktop\Gestion-d-un-refuge-animalier\Backend"
```

## Step 2 - Compile the application and prepare its dependency classpath

The following commands do not run the password reset, run tests, or connect to MySQL. Maven may download missing build dependencies.

```powershell
$refugeMaven = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
& $refugeMaven -q -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target/admin-reset-classpath.txt"
if ($LASTEXITCODE -ne 0) { throw "Build failed. Stop here; no reset has been run." }
$refugeResetClasspath = "target\classes;" + (Get-Content -LiteralPath "target\admin-reset-classpath.txt" -Raw).Trim()
```

Both variables contain paths, not passwords. The generated classpath file contains dependency paths and is under Git-ignored `target`.

## Step 3 - Optional help check (no database connection or reset)

```powershell
java --class-path $refugeResetClasspath tools\ResetAdminPassword.java --help
```

## Step 4 - Reset ONLY after approval

**Do not run this step until the procedure is approved.** This command launches the maintenance operation; it does not contain a password.

```powershell
java --class-path $refugeResetClasspath tools\ResetAdminPassword.java --reset-admin-1
```

When prompted:

1. Type `RESET 1` to confirm this exact account, or any other text to cancel before a database connection is opened.
2. Type your new password and press Enter. Nothing appears while you type; that is expected.
3. Type it again and press Enter.
4. Look for the success message stating that only administrator ID 1's password was updated. The helper never displays the password or hash.

If the entries differ or fail the existing password policy, no database connection is opened. Start again only after correcting the input. If the console is unavailable, use a normal PowerShell/Windows Terminal window without pipes, output redirection, or an IDE Run console. Do not replace the private prompt with visible text entry.

If the helper reports a guard failure, resolve the configuration/engine/trigger issue before any retry; do not remove safeguards. If commit outcome is uncertain, do not assume failure or automatically repeat the reset: restart Java and try logging in with the new password first.

## Step 5 - Restart and sign in

From the same Backend directory:

```powershell
& $refugeMaven compile exec:java
```

Keep the frontend running, or start it separately using `npm run dev` from `frontend`. Open `http://localhost:5173/admin/connexion`. Use the existing administrator email and the password you just chose. No email or other administrator detail is changed.

No application SQL import or migration is required. No automatic reset, new account, or additional web functionality is introduced.
