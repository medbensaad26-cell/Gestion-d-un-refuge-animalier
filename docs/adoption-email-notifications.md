# Adoption decision emails

When an administrator accepts or refuses an adoption request, the applicant receives an email at the address entered on the request form.

## Behaviour

- `PATCH /api/demandes/{id}/accepter` and `/refuser` keep their URLs, access rules, status codes and JSON responses.
- The email is queued only after the decision is committed. A decision that fails (401, 404, 409, 500) or is repeated sends nothing.
- Emails are sent on one background thread, so a slow mail server does not delay the API.
- A mail failure never cancels or changes a decision. Temporary failures are tried up to three times. Wrong credentials and rejected addresses are not retried.
- Failures are logged with the request ID and error type only, never the applicant's details.
- The queue is in memory. An email that still fails after its retries, or is waiting when the backend is force-stopped, is not sent later. Nothing is added to the database.

The wording is in `Backend/src/main/java/com/enicarthage/refugeanimalier/notification/MessageEmail.java`.

## Configuration

Email is optional. Without a configured SMTP host the application works as before and logs that notifications are disabled.

From `Backend`, copy the example and fill in your mail account. `email.local.properties` is ignored by Git and must not be shared.

```powershell
Copy-Item email.properties.example email.local.properties
```

| Property | Environment variable | Meaning |
|---|---|---|
| `mail.host` | `REFUGE_MAIL_HOST` | SMTP server. Leave unset to disable emails |
| `mail.port` | `REFUGE_MAIL_PORT` | Defaults to 587, or 465 with `SSL` |
| `mail.user` | `REFUGE_MAIL_USER` | SMTP login |
| `mail.password` | `REFUGE_MAIL_PASSWORD` | SMTP password |
| `mail.from` | `REFUGE_MAIL_FROM` | Sender address. Defaults to `mail.user` |
| `mail.security` | `REFUGE_MAIL_SECURITY` | `STARTTLS` (default), `SSL`, or `NONE` for a local test server without login |

`REFUGE_MAIL_CONFIG` can point to another properties file. Environment variables override the file. Restart the backend after changing these settings. The startup log then shows `Notifications email activees`. Invalid settings disable email and log which setting to correct, and the rest of the application still starts.

### Gmail example

Turn on 2-Step Verification for the sending account, create an App Password, and use it instead of the normal account password:

```properties
mail.host=smtp.gmail.com
mail.port=587
mail.user=your.address@gmail.com
mail.password=your-app-password-without-spaces
mail.from=your.address@gmail.com
mail.security=STARTTLS
```

## Tests

Automated tests never read `email.local.properties` or contact a real mail server. `EmailSettingsTest`, `NotificationEmailTest` and `AdoptionDecisionEmailTest` use a local fake SMTP server and a disposable MySQL database.
