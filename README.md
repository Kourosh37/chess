# Chess Studio

A desktop chess application built with Java 21, JavaFX 21, and Maven.

The project is organized with a clean MVC-style structure, persistent settings, save/load workflows, configurable UI themes, and a responsive 2D gameplay experience.

## Highlights

- Human vs AI and Human vs Human modes
- Three AI difficulties (`EASY`, `MEDIUM`, `HARD`)
- Smooth animated piece movement
- Touch-move rule toggle (optional)
- Per-turn time control presets
- Multiple visual themes with palette preview
- Piece style selection
- Background music + action SFX volume controls
- Auto-save strategy with one save file per active game
- Save management (load/delete/multi-select)
- Native folder picker for save directory selection

## Technology Stack

- Java 21
- JavaFX 21 (`controls`, `fxml`, `media`)
- Maven
- [`chesslib`](https://github.com/bhlangonijr/chesslib) for chess rules and legal move generation

## Architecture

Source root: `src/main/java/com/example`

- `App.java`: JavaFX entry point and stage bootstrap
- `bootstrap/`: app wiring and runtime context (`ApplicationContext`)
- `controller/`: UI orchestration (`MainController`)
- `game/`: game state, legal move flow, capture tracking
- `ai/`: AI contract + implementation
- `audio/`: music/SFX abstractions and JavaFX media implementation
- `persistence/`: settings + game save/load services
- `ui/`: board rendering and theme application
- `config/`: strongly typed app settings and enums

Resources: `src/main/resources/com/example`

- `fxml/`: screen layout
- `css/`: base and theme styles

## Performance-Oriented Design

Implemented optimizations include:

- Cached legal move generation in game service
- Diff-based board cell rendering (update only changed squares)
- Asynchronous persistence I/O on dedicated worker thread
- Lazy/dirty save list refresh to avoid unnecessary UI work
- Reduced per-frame animation overhead for board interactions

## Runtime Data

By default, runtime data is **not** stored in project resources.

- Settings file:
  - `%USERPROFILE%\\.chess-studio\\settings.properties` (Windows)
- Save files directory:
  - `%USERPROFILE%\\.chess-studio\\saves`

The save directory is configurable from **Settings** using a native folder picker.

## Build and Run

Prerequisites:

- JDK 21+
- Maven 3.9+

Commands:

```bash
mvn clean compile
mvn javafx:run
```

Package:

```bash
mvn clean package
```

## Controls and UX Notes

- `Enter` and `Escape` are supported on key screens
- Load screen supports multi-select and deletion workflows
- Confirmation overlay is custom (non-blocking app dialog style)
- In Touch-move mode, selected pieces must complete a legal move before deselection

## Project Hygiene

- Build artifacts (`target/`) are ignored
- Runtime save artifacts (`*.save`) under resources are ignored
- Editor/OS noise files are ignored

## Known Limitations

- No formal test suite yet (manual verification currently used)
- AI is depth-based and local (no external engine integration)

## License

MIT License. See `LICENSE`.
