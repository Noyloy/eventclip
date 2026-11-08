# API availability and preflight procedures

Sanitized technical adaptation only. Private identities, operational observations, approvals and result evidence are omitted. No command below has been executed for this publication snapshot. Nothing here authorizes cloud actions.

## API changes require their own approval
Read enabled services first. Enabling a missing API is a cloud mutation, not part of a read-only check. If specifically approved, enable only that named service, then read its status again. Do not link billing, grant IAM, initialize storage, upload artifacts or submit tests as side effects.

## Quota interpretation
The documented Service Usage `consumerQuotaMetrics.list` endpoint can describe effective limits and multiple plan-related metric families. It does not necessarily identify the active Firebase plan or report current consumption/reset timestamps. Iterate documented pagination completely.

Cloud Monitoring `projects.timeSeries.list` may expose quota usage metrics where available. Empty series are not zero consumption and do not provide an authoritative remaining count. Do not substitute request-rate quotas for physical/virtual test-execution limits. Verify current plan, usage and reset through an authoritative surface; historical reset-time blog posts are not current policy evidence. A local coordination lock is not a server quota reservation.

## Managed default-storage settings
Use the documented Tool Results `projects.getSettings` operation for `YOUR_FIREBASE_PROJECT_ID`. If no default bucket is configured, `projects.initializeSettings` is a separate resource-creating mutation that requires explicit approval. Do not silently create a custom bucket or assume a read-only/API-enable approval covers initialization.

After separately authorized initialization, inspect only the exact returned managed storage resource through permitted read-only operations. Review effective bucket/object access, lifecycle and any public-access or uniform-access settings. A documented default ACL is not proof of actual effective privacy. Retention must be checked against actual metadata; do not promise a fixed duration solely from documentation. If access is denied, stop at the permission boundary rather than escalate or route around it.

## Generic submission gate
Reconfirm account/project, Firebase plan, billing state, API availability, storage/privacy caveats, current shared execution budget and matching synthetic-only APK provenance. Only a separately authorized bounded submission may follow. No approval, quota balance or cloud state is asserted here.

## Official references retained from the source notes (not revalidated here)
- https://firebase.google.com/docs/test-lab/reference/toolresults/rest/v1beta3/projects/getSettings
- https://firebase.google.com/docs/test-lab/reference/toolresults/rest/v1beta3/projects/initializeSettings
- https://docs.cloud.google.com/service-usage/docs/reference/rest/v1beta1/services.consumerQuotaMetrics/list
- https://docs.cloud.google.com/monitoring/api/ref_v3/rest/v3/projects.timeSeries/list
