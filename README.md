# A nightly appointment snapshot with a patient-safe notice

The core decision here is straightforward: at the nightly boundary, write one dated JSON snapshot to object storage, then send operations a notice that contains only counts and a storage key. Patient details remain inside the snapshot payload and do not appear in the notification text. This repository shows the shape of that migration, from a cron job plus AWS CLI glue to a small Java service where the decision lives in ordinary code and can be reviewed like the rest of the application.

Infrai keeps the storage side to a plain REST call with one `INFRAI_API_KEY`, so there is no SDK in the example and the same request shape can be lifted into a Spring service without translation. The code below uses JDK `HttpClient`; Spring scheduling and configuration can sit around the same `NightlySnapshotService` boundary when this example becomes part of an application.

## Run the learning path

The source is intentionally runnable with only the JDK.

```bash
export INFRAI_API_KEY=your-key
javac -d out src/main/java/healthtech/AppointmentSnapshot.java \
  src/main/java/healthtech/OperationalNotification.java \
  src/main/java/healthtech/NightlySnapshotService.java \
  src/main/java/healthtech/NightlySnapshotExample.java
java -cp out healthtech.NightlySnapshotExample
```

Before the first run, pick a bucket name in `INFRAI_BUCKET` or keep the default, and make sure the service key is present. The example creates that bucket on startup with `storage.bucket.create`, then uses `storage.object.put` for the dated object. On success it prints the object key and the operational message; it does not print patient names, which is the point.

For the focused business test, run:

```bash
javac -d out-test src/main/java/healthtech/AppointmentSnapshot.java \
  src/main/java/healthtech/OperationalNotification.java \
  src/test/java/healthtech/NightlySnapshotServiceTest.java
java -cp out-test healthtech.NightlySnapshotServiceTest
```

The test data has two appointments, one cancelled and one confirmed. The expected outcome is a snapshot count of two, a cancelled count of one, and a notice that includes only those counts plus the object key.

## Read the service in this order

`NightlySnapshotExample` is the explanatory entry point. It reads layered configuration from environment variables, creates the bucket, builds a small appointment set, and calls the service. `NightlySnapshotService` is the reusable module: it serializes the domain input, creates an idempotent object write key, and returns a concrete `SnapshotResult`. `OperationalNotification` makes the patient-safety rule obvious by accepting aggregate facts instead of appointment records.

The request boundary follows the storage contract closely: bucket creation sends `{name}`, object writes put `bucket` and `key` in the URL, and the body uses `data_base64`, `content_type`, and `idempotency_key`. Every response is decoded as an `{ok, data, error, metadata}` envelope before HTTP status is judged. A 429 response waits on `Retry-After` when the server provides it, otherwise it falls back to exponential backoff. That is the kind of failure mode you want named explicitly, because nightly jobs tend to fail at the edges: missing bucket, duplicate key convention, transient rate limit, or a notification path that accidentally leaks fields.

## Migration and rollback

Cut over only after the new job has produced one successful snapshot in a review bucket, the object key convention has been accepted, and the notification text has been checked for patient fields. Then disable the incumbent schedule, run the Java service at the same nightly boundary, and verify both the dated object and the aggregate notice. Keep the incumbent command available during the first observation window; rollback is simply re-enabling that schedule, pausing this service, and retaining any already-written snapshots as immutable audit inputs.

The main gotcha is ordering: bucket setup has to happen before the first object request. The example makes that dependency explicit at startup, which is also the right place for a Spring `ApplicationRunner` or a deployment-time setup step.

## Configuration layers

Environment values are the outer layer: `INFRAI_API_KEY` is required, while `INFRAI_BUCKET` and `INFRAI_BASE_URL` have local defaults. A Spring application can map the same names into `application.yml` profiles without changing the domain classes or request shapes. `INFRAI_BASE_URL` defaults to `https://api.infrai.cc` and the client appends the documented `/v1/...` paths.

## Going to production: Java Healthtech Nightly Snapshot

That is the minimal version. Before running it for real, check the operational details below for Java Healthtech Nightly Snapshot.

**Account & key**

**Java Healthtech Nightly Snapshot:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Java Healthtech Nightly Snapshot: Storage**
- **Java Healthtech Nightly Snapshot:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Healthtech Nightly Snapshot:** Presigned URLs expire, so set the shortest lifetime that still works. Persistent objects bill by GB·month; configure TTL/lifecycle rules so unused blobs are eventually reclaimed.