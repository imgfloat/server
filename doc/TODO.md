* Add presets to channel admin page, which can be stored, edited, and applied to the channel. Presets store assets used and their settings. Assets uploaded are not affected by changes to presets.
* Refactor broadcast source
* Allow users to upload script asset sub-assets without first saving by making temporary files
* Create playground for script assets
    - Render on a modal canvas
* Preview script assets in marketplace
    - Render on a modal canvas
* Add text assets and font selection
* Allow custom scripts to pull recent twitch chats
    - Serve the ones that occurred since last tick to tick() or give a ~30 second window of messages
* Replace default script class with a seeding system from a filesystem directory
    - Title
    - Source code
    - Logo (optional)
    - Assets (optional)
* Set up staging server
* Buy domain
* Fix windows nt platform transparency
* Migrate broadcast view to client repository
    - Remove css and js references
    - Find a way to configure a domain for the client
