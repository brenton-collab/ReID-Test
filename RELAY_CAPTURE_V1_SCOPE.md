# Relay Capture v1 — Scope Lock

Status: LOCKED for v1
Date: 2026-08-14

## Product primitive
Relay Capture exists to move something from the physical/mobile edge into durable, intentional storage with enough context to recover and route it later.

Core invariant:

LOCAL ONLY → TRANSFER IN PROGRESS → SECURED

A local source is deleted only after the destination write is verified successful. If transfer fails, the local source remains recoverable.

## v1 capture inputs
1. Camera capture inside Relay Capture
2. Screenshot capture initiated from Relay Capture using Android's supported screen-capture permission flow
3. Import/share from another Android app via ACTION_SEND / ACTION_SEND_MULTIPLE

All three inputs converge into one staging/review flow.

## v1 staging fields
Required/available before securing:
- Category (dropdown)
- Add new category by typing a value not present
- Edit existing user categories
- Remove existing user categories
- Entity type (Person / Matter / Property / Organization / Other / None)
- Entity name or identifier
- Matter / transaction / project reference
- Optional note
- Capture source (automatic provenance: camera / screenshot / share-import)
- Capture timestamp (automatic)

The app does not perform identity resolution. Entity and matter fields are user-supplied context only.

## v1 category model
Seed categories:
- FINTRAC ID
- Client Document
- Signed Page
- Property / Problem
- Utility / Statement
- Receipt / Expense
- General Capture

Categories are locally editable. User-added categories persist on the device.

Deleting a category removes it from future choices only. It must not destroy already-secured artifacts or their provenance metadata.

## v1 destination model
- User chooses a destination intake directory once through Android Storage Access Framework / ACTION_OPEN_DOCUMENT_TREE.
- Relay Capture stores persistent URI permission to that chosen directory.
- v1 uses one canonical intake destination rather than trying to navigate the entire People hierarchy on-device.
- Routing from intake to canonical People / Matter locations occurs downstream.

## v1 file package
Each secured capture writes:
1. the original capture/imported file at useful source quality
2. a small human-readable JSON sidecar containing provenance/context

Suggested naming:
YYYY-MM-DD - [Entity or Matter if supplied] - [Category] - [sequence/source].[ext]

Sidecar includes at minimum:
- relay_schema_version
- capture_id
- captured_at
- secured_at
- source
- category
- entity_type
- entity
- matter
- note
- original_name when imported/shared
- app_version
- content mime type
- file name

## v1 secure handoff
A capture is SECURED only when Relay has:
- created the destination document
- copied all bytes
- closed/flushed the write
- confirmed the destination document exists
- confirmed a non-zero destination length when the provider exposes length

Only after SECURED may Relay delete its private temporary source.

If any step fails:
- retain local private source
- mark it pending/not secured
- surface pending state on next launch
- permit retry

## v1 privacy boundary
- Camera captures are written to app-private temporary storage, not DCIM/Camera.
- Relay does not intentionally publish new camera captures to MediaStore/Google Photos.
- No analytics, advertising SDKs, contacts, location, microphone, or broad photo-library permission.
- Storage access is limited to the user-selected SAF destination and explicitly shared/imported content.
- Screenshot capture must use Android's MediaProjection consent model; no silent screen scraping.

## v1 UX
Home screen:
- Camera
- Screenshot
- Import
- Pending indicator when unsent captures exist
- Settings / Categories

Staging screen:
- preview
- category dropdown with Add option
- entity type dropdown
- entity field
- matter field
- optional note
- Secure
- Retake/remove item
- Add another capture to same context

Success state:
✓ Secured
[Entity/context] · [count] item(s)
Saved to Relay Intake
Local temporary copies removed

Actions:
- Done
- Capture another with same context

## v1 explicit exclusions
Not in v1 unless required for safe operation:
- OCR
- AI classification or extraction
- automatic client identity resolution
- automatic filing into People subfolders
- CRM writes
- FINTRAC form preparation inside the app
- direct Google Drive API/OAuth integration
- cloud backend owned by Relay
- collaboration/multi-user accounts
- document editing
- PDF generation/scanning enhancement pipeline
- expense accounting
- notifications beyond local failure/pending status
- automatic deletion of source files owned by another app

## Design rule for scope creep
A proposed feature is v1 only if it is necessary to:
1. capture,
2. attach minimal recoverable context,
3. securely hand off,
4. verify persistence, or
5. prevent data loss/privacy leakage.

Everything else goes to post-v1 backlog.
