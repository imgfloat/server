<p align="center">
    <a href="https://imgfloat.kruhlmann.dev"><img src="src/main/resources/assets/banner.png" /></a>
</p>

[demo.webm](https://github.com/user-attachments/assets/f154ed72-6e3d-40ed-a111-706f6a916d52)

Bring your stream to life with a lightweight, real-time overlay system built for Twitch. Upload images once, place and animate them from anywhere, and see your changes live, so your stream visuals look polished.

# Getting started

## Streamers

Visit [imgfloat.kruhlmann.dev](https://imgfloat.kruhlmann.dev) to add your channel admins from the dashboard and download the application for your platform. Run the appliation, enter your twitch channel name and add the window to your OBS scene.

## Moderators

Visit [imgfloat.kruhlmann.dev](https://imgfloat.kruhlmann.dev) once your streamer has added you as a channel admin. From there you can upload images, place them on the canvas and animate them in real-time.

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
| `IMGFLOAT_GITHUB_OWNER` | Github user or org which has the client repository | Kruhlmann |
| `IMGFLOAT_GITHUB_REPO` | Client repository name | imgfloat-j |
| `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` | Maximum upload file size | 10MB |
| `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` | Maximum upload request size | 10MB |
| `IMGFLOAT_GITHUB_OWNER` | GitHub owner used to build desktop download links | Kruhlmann |
| `IMGFLOAT_GITHUB_REPO` | GitHub repo used to build desktop download links | imgfloat-j |
| `TWITCH_CLIENT_ID` | Oauth2 client id | i1bjnh4whieht5kzn307nvu3rn5pqi |
| `TWITCH_CLIENT_SECRET` | Oauth2 client secret | vpkn8cp7ona65l121j6q78l9gkmed3 |

Optional:

| Variable | Description | Example Value |
|----------|-------------|---------------|
| `TWITCH_REDIRECT_URI` | Override default redirect URI | http://localhost:8080/login/oauth2/code/twitch |

During development environment variables can be placed in the `.env` file at the project root to automatically load them. Be aware that these are only loaded when using the [Makefile](./Makefile) command `make run`.

If you want to use the default development setup your `.env` file should look like this:

```sh
TWITCH_CLIENT_ID=...
TWITCH_CLIENT_SECRET=...
IMGFLOAT_GITHUB_OWNER=...
IMGFLOAT_GITHUB_REPO=...
IMGFLOAT_INITIAL_TWITCH_USERNAME_SYSADMIN=...
```

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

### Running the electron client

There are two methods of running the electron app during development.

#### Running in the current display server

This spawns the window in your current display server.

```sh
$ make run-client
...
^C
```

#### Running in a sandboxed X server

This method spawns an Xorg server with `Xephyr` and `openbox` to ensure a floating window manager, which will more accurately reflect the common user environment. Killing either the Xephyr or electron process shuts both down.

```sh
$ make run-client-x
...
^C
```

#### Running in a sandboxed wayland server

This method spawns an Xorg server with `Xephyr` and `openbox` to ensure a floating window manager, which will more accurately reflect the common user environment. Killing either the Xephyr or electron process shuts both down.

```sh
$ make run-client-x
...
^C
```
