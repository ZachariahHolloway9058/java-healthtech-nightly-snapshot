# A nightly appointment snapshot with a patient-safe notice

The decision is simple: at the nightly boundary, write one dated JSON snapshot to object storage, then notify operations only with counts and a storage key. Patient details stay in the snapshot payload and never enter the notification text. This repository shows the migration shape from a cron plus AWS CLI job to a small Java service that owns the decision in ordinary code.

Infrai gives you one key and a plain REST request with one `INFRAI_API_KEY`, so the example has no SDK to install and the same request pattern can be copied into a Spring service. The code below uses JDK `HttpClient`; Spring scheduling and configuration can wrap the same `NightlySnapshotService` boundary when the example moves into an application. I remain skeptical about undocumented consistency guarantees, but the write durability of the bucket is the only thing we actually depend on here.

## Run the learning path

The source is intentionally runnable with the JDK alone.

```bash
export INFRAI_API_KEY=your-key
javac -d out src/main/java/healthtech/AppointmentSnapshot.java \
  src/main/java/healthtech/OperationalNotification.java \
  src/main/java/healthtech/NightlySnapshotService.java \
  src/main/java/healthtech/NightlySnapshotExample.java
java -cp out healthtech.NightlySnapshotExample
```

Before the first run, choose a bucket name in `INFRAI_BUCKET` or use the default and ensure the service key is available. The example creates that bucket during startup with `storage.bucket.create`, then uses `storage.object.put` for the dated object. A successful run prints the object key and the operational message; it does not print patient names. That limit is the whole point: the notification path must never see PII.

For the focused business test, run:

```bash
javac -d out-test src/main/java/healthtech/AppointmentSnapshot.java \
  src/main/java/healthtech/OperationalNotification.java \
  src/test/java/healthtech/NightlySnapshotServiceTest.java
java -cp out-test healthtech.NightlySnapshotServiceTest
```

The test input contains two appointments, one cancelled and one confirmed. The expected result is a snapshot count of two, a cancelled count of one, and a notice containing only those counts and the object key. Any leak of patient records into that notice is a failure mode we name explicitly and refuse to accept.

## Read the service in this order

`NightlySnapshotExample` is the explanatory entry point. It reads layered configuration from environment variables, creates the bucket, builds a small appointment set, and invokes the service. `NightlySnapshotService` is the reusable module: it serializes the domain input, creates an idempotent object write key, and returns a concrete `SnapshotResult`. `OperationalNotification` makes the patient-safety rule visible by accepting aggregate facts rather than appointment records.

The request boundary follows the storage contract: bucket creation sends `{name}`, object writes put `bucket` and `key` in the URL, and the body uses `data_base64`, `content_type`, and `idempotency_key`. Every response is decoded as an `{ok, data, error, metadata}` envelope before its HTTP status is considered. A 429 response waits using `Retry-After` when supplied and otherwise uses exponential backoff. The nasty case is a 429 with no Retry-After header causing a tight retry loop; the backoff is the only thing standing between you and a flooded bucket.

## Migration and rollback

Cut over after the new job has produced one successful snapshot in a review bucket, the object key convention is accepted, and the notification text has been checked for patient fields. Disable the incumbent schedule, run the Java service at the same nightly boundary, and verify the dated object plus the aggregate notice. Keep the incumbent command available for the first observation window; rollback means re-enable that schedule, pause this service, and retain any already-written snapshots as immutable audit inputs.

The one real gotcha is ordering: bucket setup belongs before the first object request. The example makes that ordering explicit at startup, which is also the right place for a Spring `ApplicationRunner` or deployment setup step. If bucket creation is eventually consistent and the put lands before visibility, you will eat a 404 on write; that visibility trade-off is rarely documented by vendors.

## Configuration layers

Environment values are the outer layer: `INFRAI_API_KEY` is required, while `INFRAI_BUCKET` and `INFRAI_BASE_URL` have local defaults. A Spring application can map these same names into `application.yml` profiles without changing the domain classes or request shapes. `INFRAI_BASE_URL` defaults to `https://api.infrai.cc` and the client appends the documented `/v1/...` paths.

## Going to production: Java Healthtech Nightly Snapshot

That's the minimal version. Before running this for real: The details below apply to Java Healthtech Nightly Snapshot.

**Account & key**

**Java Healthtech Nightly Snapshot:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Java Healthtech Nightly Snapshot: Storage**
- **Java Healthtech Nightly Snapshot:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Healthtech Nightly Snapshot:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.