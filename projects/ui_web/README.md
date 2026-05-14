# Codenames web server/UI (biff)

A [Biff](https://biffweb.com/)-powered Codenames web app.

Run `bb dev` to get started. See `bb tasks` for other commands.

## Known issues / To-do

* [ ] Opening a second tab on the same game for the same player replaces the old
      websocket
* [x] The settings screen only shows the matches that you've created, not the
      ones you have joined
* [ ] Old websockets are not cleaned up when a match is deleted
* [x] Fix Play! button animation (`wiggle` → `animate-wiggle`)
* [x] Better card text sizing for long codenames (more font-size breakpoints)
* [x] Remove duplicate unused modal div in app.clj (lines ~481-490)
* [x] Visual turn indicator (pulsing border/highlight on active team panel)
* [x] Nickname UI (schema supports `player/nick` but no form to set it)
* [x] Indicate which player is the current user in team panels
* [x] Settings: show all matches the player is part of, not just created ones
* [ ] Match join mechanism (lobby, join-by-code, or join-by-link)
* [ ] WebSocket reconnection with fallback polling
* [ ] Multi-tab support (track connections by player+session, not just player)
* [ ] In-game event log sidebar (recent clues, reveals, player joins)
* [ ] Fix 0 clues (0 is the same as infinity)
* [ ] Support infinity clues
* [ ] Add localization support
