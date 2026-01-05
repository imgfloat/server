# Imgfloat

A Spring Boot overlay server for Twitch broadcasters and their channel admins. Broadcasters can authorize via Twitch OAuth and invite channel admins to manage images that float over a transparent canvas. Updates are pushed in real time over WebSockets so OBS browser sources stay in sync.

## Running

Define the following required environment variables:

| Variable | Description | Example Value |
|----------|-------------|---------------|
| IMGFLOAT_ASSETS_PATH | Filesystem path to store uploaded images | /var/imgfloat/assets |
| IMGFLOAT_PREVIEWS_PATH | Filesystem path to store generated image previews | /var/imgfloat/previews |
| IMGFLOAT_DB_PATH | Filesystem path to the SQLite database file | /var/imgfloat/imgfloat.db |
| IMGFLOAT_INITIAL_TWITCH_USERNAME_SYSADMIN | Twitch username of the initial sysadmin user | example_broadcaster |
| SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE | Maximum upload file size | 10MB |
| SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE | Maximum upload request size | 10MB |
| TWITCH_CLIENT_ID | Oauth2 client id | i1bjnh4whieht5kzn307nvu3rn5pqi |
| TWITCH_CLIENT_SECRET | Oauth2 client secret | vpkn8cp7ona65l121j6q78l9gkmed3 |

Optional:

| Variable | Description | Example Value |
|----------|-------------|---------------|
| TWITCH_REDIRECT_URI | Override default redirect URI | http://localhost:8080/login/oauth2/code/twitch |
