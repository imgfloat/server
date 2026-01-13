const TWITCH_IRC_URL = "wss://irc-ws.chat.twitch.tv:443";
const ANON_PREFIX = "justinfan";
const ANON_PASSWORD = "SCHMOOPIIE";

const buildAnonymousNick = () => {
    const suffix = Math.floor(Math.random() * 100000)
        .toString()
        .padStart(5, "0");
    return `${ANON_PREFIX}${suffix}`;
};

const parseTags = (rawTags) => {
    if (!rawTags) {
        return {};
    }

    return rawTags.split(";").reduce((acc, entry) => {
        const [key, value = ""] = entry.split("=");
        acc[key] = value;
        return acc;
    }, {});
};

const parseIrcMessage = (rawLine) => {
    let line = rawLine;
    let tags = {};
    let prefix = "";

    if (line.startsWith("@")) {
        const spaceIndex = line.indexOf(" ");
        tags = parseTags(line.slice(1, spaceIndex));
        line = line.slice(spaceIndex + 1);
    }

    if (line.startsWith(":")) {
        const spaceIndex = line.indexOf(" ");
        prefix = line.slice(1, spaceIndex);
        line = line.slice(spaceIndex + 1);
    }

    const commandEnd = line.indexOf(" ");
    const command = commandEnd === -1 ? line : line.slice(0, commandEnd);
    const params = commandEnd === -1 ? "" : line.slice(commandEnd + 1);

    return {
        command,
        params,
        prefix,
        tags,
        raw: rawLine,
    };
};

const extractChatMessage = (ircMessage) => {
    if (ircMessage.command !== "PRIVMSG") {
        return null;
    }

    const messageSplit = ircMessage.params.split(" :");
    const channel = messageSplit[0]?.trim() || "";
    const message = messageSplit.slice(1).join(" :");
    const displayName = ircMessage.tags["display-name"] || ircMessage.prefix.split("!")[0];

    return {
        channel,
        displayName,
        message,
        tags: ircMessage.tags,
        prefix: ircMessage.prefix,
        raw: ircMessage.raw,
    };
};

export const connectTwitchChat = (channelName, onMessage = console.log) => {
    if (!channelName) {
        console.warn("Twitch chat connection skipped: missing channel name");
        return () => {};
    }

    const normalizedChannel = channelName.toLowerCase();
    const nick = buildAnonymousNick();
    const socket = new WebSocket(TWITCH_IRC_URL);

    socket.addEventListener("open", () => {
        socket.send(`PASS ${ANON_PASSWORD}`);
        socket.send(`NICK ${nick}`);
        socket.send("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership");
        socket.send(`JOIN #${normalizedChannel}`);
    });

    socket.addEventListener("message", (event) => {
        const lines = String(event.data).split("\r\n").filter(Boolean);

        lines.forEach((line) => {
            if (line.startsWith("PING")) {
                const payload = line.split(" ")[1] || ":tmi.twitch.tv";
                socket.send(`PONG ${payload}`);
                return;
            }

            const parsed = parseIrcMessage(line);
            const chatMessage = extractChatMessage(parsed);

            if (chatMessage) {
                onMessage({
                    channel: chatMessage.channel,
                    displayName: chatMessage.displayName,
                    message: chatMessage.message,
                    tags: chatMessage.tags,
                    prefix: chatMessage.prefix,
                    raw: chatMessage.raw,
                });
            }
        });
    });

    socket.addEventListener("close", () => {
        console.info(`Twitch chat connection closed for #${normalizedChannel}`);
    });

    socket.addEventListener("error", (event) => {
        console.error("Twitch chat connection error", event);
    });

    return () => {
        socket.close();
    };
};
