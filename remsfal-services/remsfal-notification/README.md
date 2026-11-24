# REMSFAL Notification Microservice (Backend)

This microservice is responsible for all customer notification functionalities.

## Service-specific Prerequisites

The notification microservice stores user preferences (e.g. which issue emails a user receives) in its own
PostgreSQL database `remsfal_notification`. The database is created by the shared `postgres` container
(see [docker-compose.yml](../docker-compose.yml)); the schema is managed by Liquibase on startup.

Since the notification microservice is designed to send emails, it needs an outgoing SMTP server.
**For local development no SMTP account is required:** in dev mode all emails are delivered to
[Mailpit](https://mailpit.axllent.org/), which is started together with the other infrastructure
containers (`docker compose up -d` in the repository root, see [docker-compose.yml](docker-compose.yml)).

The notification microservice reads files (e.g. profile photos for emails) from the S3 bucket `remsfal`,
which is shared with the platform service. Locally the `localstack` container is used, in tests the S3 Dev Services.
In production the notification service only needs **read access**: use separate credentials that may only
read objects from the shared bucket (`s3:GetObject`) and disable the bucket creation on startup:

```properties
remsfal.storage.bucket-name=remsfal
remsfal.storage.create-bucket=false
quarkus.s3.aws.credentials.static-provider.access-key-id=...
quarkus.s3.aws.credentials.static-provider.secret-access-key=...
```

The most important mailing configuration can be found in the
[Quarkus Mailer Configuration Reference](https://quarkus.io/guides/mailer-reference#configuration-reference).

### Mailpit (default in dev mode)

Mailpit is a local SMTP server that catches **every** outgoing email, regardless of the recipient address,
and shows it in a web UI:

| What        | Where                                          |
|-------------|------------------------------------------------|
| Web UI      | [http://localhost:8025](http://localhost:8025) |
| SMTP server | `localhost:2525` (no TLS, no authentication)   |

In the web UI you can inspect the HTML and plain text versions of each email, check links, view the
raw source and headers, and delete messages. Because nothing is delivered to real mailboxes, you can
use the fictitious seed users of the platform service (`…@remsfal.dev`) and still see every notification
they would receive.

The dev defaults in [application.properties](src/main/resources/application.properties) are:

```properties
%dev.quarkus.mailer.from=no-reply@remsfal.dev
%dev.quarkus.mailer.mock=false
%dev.quarkus.mailer.host=localhost
%dev.quarkus.mailer.port=2525
%dev.quarkus.mailer.tls=false
```

### Sending real emails in dev mode (testing)

To check how emails look in real mail clients, you can point the dev mode at a real SMTP server.
Passwords must never be committed, so put the configuration into an
[`.env` file](https://quarkus.io/guides/config-reference#env-file) at
`./remsfal-services/remsfal-notification/.env` (`.env` files are ignored by Git).
Values from the `.env` file take precedence over the Mailpit defaults in `application.properties`.

Example for an HTW account:

```shell script
QUARKUS_MAILER_FROM=FirstName.LastName@htw-berlin.de
QUARKUS_MAILER_USERNAME=s0000001
QUARKUS_MAILER_PASSWORD=<your password>
QUARKUS_MAILER_HOST=mail.htw-berlin.de
QUARKUS_MAILER_PORT=465
QUARKUS_MAILER_TLS=true
```
> **_NOTE:_**  Use your HTW account username (e.g. s0000001 or mustermann; but not your email alias FirstName.LastName).

Example for a Gmail account (requires 2-step verification and an
[app password](https://support.google.com/accounts/answer/185833)):

```shell script
QUARKUS_MAILER_FROM=your.name@gmail.com
QUARKUS_MAILER_USERNAME=your.name@gmail.com
QUARKUS_MAILER_PASSWORD=<16 character app password>
QUARKUS_MAILER_HOST=smtp.gmail.com
QUARKUS_MAILER_PORT=587
QUARKUS_MAILER_TLS=false
QUARKUS_MAILER_START_TLS=REQUIRED
```

> **_WARNING:_** With a real SMTP server, emails are sent to real recipients. The seed users
> (`…@remsfal.dev`) are not deliverable. To receive the emails yourself, log in through the dev login with
> your own email address (the dev login accepts any address).

Remove the SMTP overrides from `.env` to switch back to Mailpit.

> **_NOTE:_** `QUARKUS_MAILER_SSL` is deprecated, use `QUARKUS_MAILER_TLS` instead. If you still have an
> `.env` file from an older setup that contains `_DEV_QUARKUS_MAILER_MOCK=false`, it is no longer needed.

## Browser push notifications

Generate a VAPID key pair once per environment with `npx --yes web-push generate-vapid-keys --json`.
Set these variables on the notification service. For local development, put them in this module's
Git-ignored `.env` and start the service from this module.

| Variable | Value |
|----------|-------|
| `DE_REMSFAL_WEB_PUSH_PUBLIC_KEY` | Generated `publicKey` |
| `DE_REMSFAL_WEB_PUSH_PRIVATE_KEY` | Generated `privateKey`, stored as a secret |
| `DE_REMSFAL_WEB_PUSH_SUBJECT` | A contact URI such as `mailto:admin@example.org` |

Set all three variables together. Leaving all three unset disables browser push. REMSFAL serves the
public key at `/notification/v1/web-push/public-key` for browser registration. Keep the private key
on the notification service. Keep the key pair stable across restarts. Changing the public key
requires browsers to subscribe again.

For local browser push, use `localhost` and start Vite from the frontend checkout with
`VITE_SERVICE_WORKER_ENABLED=true npm run dev`. For a deployed frontend, use HTTPS and enable
the service worker. See the [frontend configuration](../../../Frontend/README.md#configuration).

Enable browser notifications and send a test notification from the top bar of the signed-in app.
To test an issue notification, create a tenant report and use the manager's issue form to change only
its status. When an issue is visible to tenants, a status change notifies the reporting tenant
if their account belongs to the linked rental agreement. For an issue created by a manager,
the tenant accounts linked to the agreement receive the status notification. The account that
changed the status is excluded.

## Native push notifications

The top bar uses a separate native subscription API at `/notification/v1/push-subscriptions`.
Registration accepts the Capacitor device token and the transport (`FCM` for Android,
`APNS` for iOS). The API returns a subscription ID. The test and delete operations use
that ID and are restricted to the owner. The test targets one installation.
Issue status updates use the same recipient rule across browser and native push.

| Variable | Purpose |
|----------|---------|
| `FCM_SERVICE_ACCOUNT_PATH` | Path to the Firebase service account JSON used for Android push |
| `APNS_TEAM_ID` | Apple Developer Team ID |
| `APNS_KEY_ID` | Key ID of the APNs signing key |
| `APNS_PRIVATE_KEY_PATH` | Path to the APNs `.p8` private key |
| `APNS_TOPIC` | App bundle ID, defaults to `de.remsfal.app` |
| `APNS_SANDBOX` | Match the app's signed APNs entitlement. Defaults to `true` in dev and `false` otherwise |

For local development, put these variables in this module's Git-ignored `.env`. Use paths
readable by the notification service and keep the private files outside version control.
Restart the notification service after making configuration changes. Start the shared Compose
infrastructure and the platform service for local login and JWT verification.

For **Android**, use a service account authorized to send to the Firebase project that issued
the app's FCM tokens. Get the Android app configuration file from that project and put it at
`Frontend/android/app/google-services.json`.
[Firebase Android setup](https://firebase.google.com/docs/android/setup)

For **iOS**, the notification service talks directly to APNs. Use an APNs-enabled signing key
from the Apple team that owns the app ID. For a development-signed build, the configuration
looks like this:

```dotenv
APNS_TEAM_ID=YOUR_TEAM_ID
APNS_KEY_ID=YOUR_KEY_ID
APNS_PRIVATE_KEY_PATH=/absolute/path/to/AuthKey_YOUR_KEY_ID.p8
APNS_TOPIC=de.remsfal.app
APNS_SANDBOX=true
```

Match `APNS_TOPIC` to the Xcode bundle identifier and `APNS_SANDBOX` to the signed app's
`aps-environment`. Use `true` for `development` and `false` for `production`, including TestFlight.
Each notification service instance sends to one APNs environment.
[Apple APNs entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/aps-environment)

See [native push setup in the frontend](../../../Frontend/README.md#native-push-setup) for the
Firebase app setup and the Apple Developer, CocoaPods and Xcode steps.

### Push troubleshooting

| Test endpoint response | Meaning |
|------------------------|---------|
| `204` | The push service accepted the request. Check the target browser or device for display. |
| `404` | The subscription ID could not be found for the signed-in user. Register the current installation again. |
| `410` | The push service reported an expired subscription. Register the current installation again. |
| `502` | Delivery to the push service failed. Check the notification service logs and the provider configuration. |
| `503` | The selected push transport is unavailable. Check its environment variables on the notification service. |

## Running the application in dev mode

You can run this microservice in dev mode that enables live coding using:

```shell script
mvn clean install
mvn compile quarkus:dev -pl remsfal-services/remsfal-notification
```

> **_NOTE:_**  Quarkus ships with a Dev UI, which is available in dev mode only at http://localhost:8082/q/dev/.


## Running the application for production

We recommend using ready-made container images for productive use. A complete list of all available microservices can be found in the [GitHub Container Registry](https://github.com/remsfal/remsfal-backend/pkgs/container/remsfal-backend).

### Mailer configuration

In production, configure the SMTP server via environment variables (or your deployment's secret store).
Never put credentials into `application.properties`.

| Environment variable           | Example                  | Description                                        |
|--------------------------------|--------------------------|----------------------------------------------------|
| `QUARKUS_MAILER_FROM`          | `no-reply@remsfal.de`    | Sender address                                     |
| `QUARKUS_MAILER_HOST`          | `smtp.example.org`       | SMTP host                                          |
| `QUARKUS_MAILER_PORT`          | `465` or `587`           | SMTP port                                          |
| `QUARKUS_MAILER_TLS`           | `true` (port 465)        | Implicit TLS                                       |
| `QUARKUS_MAILER_START_TLS`     | `REQUIRED` (port 587)    | STARTTLS, use instead of `TLS` for port 587        |
| `QUARKUS_MAILER_USERNAME`      | `no-reply@remsfal.de`    | SMTP user                                          |
| `QUARKUS_MAILER_PASSWORD`      | *(secret)*               | SMTP password                                      |

Make sure that the sender domain is allowed to send via your SMTP server (SPF record) and that the
server signs outgoing emails (DKIM); otherwise notifications are likely to end up in spam folders.
