# Server

# Contributing

## Running locally

### Environment

Define the following required environment variables:

| Variable | Description | Example Value |
|----------|-------------|---------------|
| `IMGFLOAT_ASSETS_PATH` | Filesystem path to store uploaded assets | /var/imgfloat/assets |
| `IMGFLOAT_PREVIEWS_PATH` | Filesystem path to store generated image previews | /var/imgfloat/previews |
| `IMGFLOAT_DB_PATH` | Filesystem path to the SQLite database file | /var/imgfloat/imgfloat.db |
| `IMGFLOAT_INITIAL_TWITCH_USERNAME_SYSADMIN` | Twitch username of the initial sysadmin user | example_broadcaster |
| `IMGFLOAT_GITHUB_CLIENT_OWNER` | GitHub user or org which has the client repository | imgfloat |
| `IMGFLOAT_GITHUB_CLIENT_REPO` | Client repository name | client |
| `IMGFLOAT_GITHUB_CLIENT_VERSION` | Client release version used for download links | 1.2.3 |
| `IMGFLOAT_TOKEN_ENCRYPTION_KEY` | Base64/Base64URL-encoded 256-bit (32 byte) key used to encrypt OAuth tokens at rest (store in a secret manager or KMS) | x5A8tS8Lk4q2qY0xRkz8r9bq2bx0R4A9a0m0k5Y8mCk= |
| `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` | Maximum upload file size | 10MB |
| `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` | Maximum upload request size | 10MB |
| `TWITCH_CLIENT_ID` | Oauth2 client id | i1bjnh4whieht5kzn307nvu3rn5pqi |
| `TWITCH_CLIENT_SECRET` | Oauth2 client secret | vpkn8cp7ona65l121j6q78l9gkmed3 |

Optional:

| Variable | Description | Example Value |
|----------|-------------|---------------|
| `IMGFLOAT_COMMIT_URL_PREFIX` | Git commit URL prefix used for the build link badge (unset to hide the badge) | https://github.com/imgfloat/server/commit/ |
| `IMGFLOAT_DOCS_URL` | Base URL for Imgfloat documentation links | https://docs.imgflo.at |
| `IMGFLOAT_IS_STAGING` | Show a staging warning banner on non-broadcast pages when set to `1` | 1 |
| `IMGFLOAT_MARKETPLACE_SCRIPTS_PATH` | Filesystem path to marketplace script seed directories (each containing `metadata.json`, optional `source.js`, optional `logo.png`, and optional `attachments/`) | /var/imgfloat/marketplace-scripts |
| `IMGFLOAT_SYSADMIN_CHANNEL_ACCESS_ENABLED` | Allow sysadmins to manage any channel without being listed as a channel admin | true |
| `TWITCH_REDIRECT_URI` | Override default redirect URI | http://localhost:8080/login/oauth2/code/twitch |
| `IMGFLOAT_TOKEN_ENCRYPTION_PREVIOUS_KEYS` | Comma-delimited base64 keys to allow decryption after key rotation (oldest last) | oldKey1==,oldKey2== |

OAuth tokens are encrypted at rest using the key provided by `IMGFLOAT_TOKEN_ENCRYPTION_KEY` (you can generate it with `openssl rand -base64 32`. Store this key in a secret manager or KMS and inject it via environment variables or a secret provider in production. When rotating keys, update `IMGFLOAT_TOKEN_ENCRYPTION_KEY` with the new key and populate `IMGFLOAT_TOKEN_ENCRYPTION_PREVIOUS_KEYS` with the old keys so existing tokens can be decrypted. After rotation, re-authenticate users or clear the `oauth2_authorized_client` table to re-encrypt tokens with the new key.

During development environment variables can be placed in the `.env` file at the project root to automatically load them. Be aware that these are only loaded when using the [Makefile](./Makefile) command `make run`.

If you want to use the default development setup your `.env` file should look like this:

```sh
TWITCH_CLIENT_ID=...
TWITCH_CLIENT_SECRET=...
IMGFLOAT_GITHUB_CLIENT_OWNER=...
IMGFLOAT_GITHUB_CLIENT_REPO=...
IMGFLOAT_GITHUB_CLIENT_VERSION=...
IMGFLOAT_INITIAL_TWITCH_USERNAME_SYSADMIN=...
```

### Marketplace seed scripts

To pre-seed marketplace scripts from the filesystem, set `IMGFLOAT_MARKETPLACE_SCRIPTS_PATH` to a directory of script seed folders. Each folder name can be anything (it is not used for display); the folder name is the listing identifier and the metadata controls the title.

Each script folder supports the following structure (files marked optional can be omitted). The folder name is used as the marketplace script identifier, so keep it stable even if the display name changes:

```
marketplace-scripts/
  <any-folder-name>/
    metadata.json            # required
    source.js                # required (script source)
    logo.png                 # optional (logo image)
    attachments/             # optional (additional attachments)
      <any-filename>
      rotate.png             # optional (example attachment copied from logo)
```

`metadata.json` fields:

```json
{
  "name": "Script display name",
  "description": "Short description",
}
```

Only `name` is required. The folder name is used to identify the marketplace listing; when a script is imported, the asset receives a new generated ID. Media types are inferred from the files on disk. Attachments are loaded from the `attachments/` folder and appear in the imported script's attachments list, referenced by filename (for example `rotate.png`). Attachment filenames must be unique within a script. The logo is optional and remains separate from attachments; if you want to use the same image inside the script, add a copy of it under `attachments/`.

### Build and run

To run the application:

```sh
$ make run
...
...  : Tomcat started on port 8080 (http) with context path ''
```

If you want live compilation run the `watch` command in a separate terminal. Note that this doesn't automatically reload the browser; this has to be done manually.

```sh
$ make watch
...
[INFO] BUILD SUCCESS
```

This automatically re-compiles the project when source files change. `entr` is required for this to work.
