# Animal photos

An administrator can add, replace or remove one photo per animal from the animal form. The photo then appears on the home page, the animal page, the adoption form and the administrator list. Animals without a photo keep the existing illustration and its "Photo illustrative" label. The matching page still shows illustrations.

## Storage

Photos are files named from the animal ID, such as `animal-12.webp`. The database schema is unchanged and no migration is needed.

| Setting | Location |
|---|---|
| Default | `Backend/uploads/animaux`, ignored by Git |
| `REFUGE_UPLOAD_DIR` | `<configured folder>/animaux` |

Include this folder in backups and when moving the application to another computer, because the database does not contain the photos. Deleting an animal also deletes its photo. Automated tests use temporary folders only.

## HTTP API

| Route | Access | Behaviour |
|---|---|---|
| `GET /api/animaux/{id}/image` | Public | Returns the photo, or 404 |
| `PUT /api/animaux/{id}/image` | Administrator | Body is the raw file, with `Content-Type` set to its image type. Returns `imageUrl` |
| `DELETE /api/animaux/{id}/image` | Administrator | Removes the photo if there is one |

Accepted formats are JPEG, PNG and WebP, up to 5 MB. The server checks the file's real signature and refuses SVG and other formats. Refusals use 400 (empty), 413 (too large) or 415 (unsupported type).

`GET /api/animaux` and `GET /api/animaux/{id}` add `imageUrl` only when a photo exists. `POST /api/animaux` now also returns the new animal's `id`.
