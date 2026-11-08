# Read-only verification procedure

Sanitized technical adaptation only. Private identities, operational observations, approvals and result evidence are omitted. No command below has been executed for this publication snapshot. Nothing here authorizes cloud actions.

## Verify independently before any approved upload
1. Confirm the intended account and project through the sanctioned CLI/API, without printing credentials or copying auth configuration. Use `YOUR_FIREBASE_PROJECT_ID` and `YOUR_CLOUD_PROJECT_NUMBER` placeholders until configured locally.
2. Inspect Cloud Billing status separately from the Firebase plan. Billing-disabled is not independent proof of the Firebase plan; an API schema without a plan field cannot establish it. If documented access is denied, stop and report the unavailable check; do not try undocumented endpoints or alternate credentials.
3. Inventory required enabled APIs: Cloud Testing, Tool Results, Service Usage and any separately needed Storage/Monitoring APIs. A read-only preflight does not authorize enabling services.
4. Query the current supported device/API/ABI/locale catalog. Catalog availability is not proof of APK ABI support, codec behavior or device acceptance. Do not silently substitute devices or expand a matrix.
5. Inspect managed default-storage settings directly. Customer bucket inventory alone cannot establish whether Test Lab's centrally managed default bucket exists. Project IAM alone cannot prove bucket/object privacy. Review exact effective access, lifecycle and any inspection limits.
6. Verify current shared-project execution usage/remaining and reset metadata. Execution limits, request quotas and plan names are different facts. No buckets, no visible history, or absent monitoring series do not imply unused quota. Do not infer the reset timezone; label any converted time explicitly.
7. Match the reviewed app/test hashes, signatures, package, target configuration, permissions and synthetic-fixture provenance before any separately approved run. Use one selected instrumentation method and an explicit bounded duration; no automatic retries, extra devices or navigation tests.

Local evidence freshness rules and a local reservation cannot reserve server quota or serialize other users. Unknown checks stay unknown; no private operational checklist is published. Command templates require explicit local configuration and separate approval.
