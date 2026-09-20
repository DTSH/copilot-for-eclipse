# Prompt zur erneuten Umsetzung von `agent-mcp-config-fix`

## Ziel

Setze den Fix für Custom-Agent-Toolkonfigurationen erneut um. Die Umsetzung muss sicherstellen, dass jeder Custom
Agent eine eigene, über seine Mode-ID adressierte Toolauswahl besitzt und nie stillschweigend die globale Auswahl des
Built-in-`agent-mode` erbt. Lies vor Beginn `agent-mcp-config-fix.md` und gleiche die dort beschriebenen Regeln mit den
unten ergänzten Details ab.

Vergleichsbasis dieser Analyse war der letzte lokale Commit:

- `fada2d74` (`HEAD -> agent-mcp-config-fix`, `origin/agent-mcp-config-fix`) – `target name fix`

Die lokalen Änderungen gegenüber diesem Commit lagen vollständig im Index und betrafen Produktionscode, Tests,
Dokumentation sowie Eclipse-Projekteinstellungen.

## Betroffene Dateien aus dem lokalen Diff

### Produktionscode

- `com.microsoft.copilot.eclipse.core/src/com/microsoft/copilot/eclipse/core/chat/CustomChatMode.java`
- `com.microsoft.copilot.eclipse.core/src/com/microsoft/copilot/eclipse/core/chat/CustomChatModeManager.java`
- `com.microsoft.copilot.eclipse.ui/src/com/microsoft/copilot/eclipse/ui/chat/ActionBar.java`
- `com.microsoft.copilot.eclipse.ui/src/com/microsoft/copilot/eclipse/ui/chat/services/AgentToolService.java`
- `com.microsoft.copilot.eclipse.ui/src/com/microsoft/copilot/eclipse/ui/chat/services/McpConfigService.java`
- `com.microsoft.copilot.eclipse.ui/src/com/microsoft/copilot/eclipse/ui/chat/services/UserPreferenceService.java`
- `com.microsoft.copilot.eclipse.ui/src/com/microsoft/copilot/eclipse/ui/preferences/CustomAgentToolStatusResolver.java`
- `com.microsoft.copilot.eclipse.ui/src/com/microsoft/copilot/eclipse/ui/preferences/LanguageServerSettingManager.java`
- `com.microsoft.copilot.eclipse.ui/src/com/microsoft/copilot/eclipse/ui/preferences/McpPreferencePage.java`
- `com.microsoft.copilot.eclipse.ui/src/com/microsoft/copilot/eclipse/ui/utils/McpUtils.java`

### Tests

- `com.microsoft.copilot.eclipse.ui.test/src/com/microsoft/copilot/eclipse/ui/preferences/CustomAgentToolStatusResolverTest.java`
- `com.microsoft.copilot.eclipse.ui.test/src/com/microsoft/copilot/eclipse/ui/preferences/LanguageServerSettingManagerTests.java`
- `com.microsoft.copilot.eclipse.ui.test/src/com/microsoft/copilot/eclipse/ui/utils/McpUtilsTests.java`

### Einstellungen und Dokumentation

- `agent-mcp-config-fix.md`
- `.settings/org.eclipse.core.resources.prefs`
- `com.microsoft.copilot.eclipse.swtbot.test/.settings/org.eclipse.core.resources.prefs`
- `com.microsoft.copilot.eclipse.swtbot.test/.settings/org.eclipse.core.runtime.prefs`

## Fachliche Anforderungen

1. **Mode-Isolation**
   - `agent-mode` und jeder Custom Agent müssen getrennte Einträge in `MCP_TOOLS_MODE_STATUS` verwenden.
   - Custom Agents dürfen weder beim Start noch zur Laufzeit den globalen `agent-mode`-Status erben.
   - Legacy-Status aus `MCP_TOOLS_STATUS` darf nur für `agent-mode` übernommen werden.

2. **Unterscheidung zwischen fehlender und expliziter `tools`-Liste**
   - `CustomChatMode` muss unterscheiden können, ob `tools` in der Agent-Datei fehlt oder explizit vorhanden ist.
   - Bei `ConversationMode.getCustomTools() == null` gilt: kein expliziter `tools`-Eintrag.
   - Bei `tools: []` gilt: expliziter Eintrag mit leerer Liste; alle verfügbaren Tools sind deaktiviert.
   - Der einfache Konstruktor `CustomChatMode(String id, String displayName, String description)` wurde lokal als
     explizite leere Liste behandelt. Prüfe bei einer erneuten Umsetzung, ob dieser Konstruktor wirklich einen neu
     erzeugten Agent-Template-Zustand repräsentiert; falls nicht, vereinfache ihn auf das LSP-basierte Verhalten.

3. **Auflösung expliziter Toollisten**
   - Toolangaben ohne Slash gehören zum Built-in-Tool-Server, dessen UI-Schlüssel derzeit
     `Messages.preferences_page_mcp_tools_builtin` ist.
   - Toolangaben mit Slash werden am letzten Slash getrennt, damit MCP-Servernamen selbst Slashes enthalten dürfen,
     z. B. `company/platform/search` → Server `company/platform`, Tool `search`.
   - Blank-Werte, leere Servernamen und leere Toolnamen werden ignoriert.
   - Wenn ein vollständiges Toolinventar bekannt ist, müssen alle verfügbaren, aber nicht explizit genannten Tools im
     Mode-Status mit `false` enthalten sein.

4. **Initialisierung von Custom Agents ohne `tools`**
   - Existiert bereits ein mode-spezifischer Preference-Eintrag, bleibt dieser maßgeblich.
   - Existiert noch kein Eintrag, wird einmalig das vollständige aktuelle Inventar aus Built-in-Tools und MCP-Tools mit
     `true` aktiviert.
   - Die aktuelle Implementierung wartet nicht blockierend, sondern führt die Initialisierung erst aus, wenn beide
     Inventarquellen gesetzt sind. Das geschieht durch einen `null`-Guard für Built-in- und MCP-Inventar.
   - Später hinzukommende Tools werden für bereits initialisierte Custom Agents nicht automatisch aktiviert.

5. **Language-Server-Synchronisierung**
   - Beim Start müssen alle gespeicherten Mode-Einträge aus `MCP_TOOLS_MODE_STATUS` an den Language Server übertragen
     werden.
   - Für Custom Agents muss die `file://`-Mode-ID an beide Endpunkte gesetzt werden:
     - MCP-Server-Tools: `updateMcpToolsStatus`
     - Built-in-Tools: `updateConversationToolsStatus`
   - Für `agent-mode` darf `customChatModeId` nicht gesetzt werden.
   - Der Hilfscheck soll nur IDs mit Präfix `file://` als Custom-Mode-ID behandeln.
   - `mcpToolStatusInitialized` steuert, dass während früher Inventarsammlung nicht zu früh LSP-Updates verschickt
     werden; nach Initialisierung werden geänderte Custom-Agent-Status nachgezogen.

6. **Inventarflüsse**
   - `AgentToolService` muss die vom Language Server zurückgemeldeten Built-in-Tools an
     `LanguageServerSettingManager.updateAvailableBuiltInTools(...)` weitergeben.
   - `McpConfigService` muss `ON_DID_CHANGE_MCP_TOOLS` auf echte `McpServerToolsCollection`-Einträge filtern, dieses
     MCP-Inventar an `updateAvailableMcpTools(...)` melden und erst danach einmalig `initializeMcpToolsStatus()`
     auslösen.
   - `UserPreferenceService` muss auf `TOPIC_CHAT_DID_CHANGE_CUSTOMIZATION_FILES` mit `CustomizationType.AGENT`
     reagieren, Custom Modes neu laden und danach die Tool-Preferences synchronisieren.
   - Mehrere Reloads sollen serialisiert werden, damit ältere asynchrone Loads keine neueren Ergebnisse überschreiben.

7. **Asynchrone Custom-Mode-Ladevorgänge**
   - `CustomChatModeManager` verwendet eine Generation (`AtomicLong`), damit ein älterer asynchroner Load nach einem
     neueren Reload die `customModes`-Liste nicht zurücksetzt.
   - Der entfernte, ungenutzte `SEPARATOR_PREFIX` darf nicht wieder eingeführt werden.

8. **Preference-Seite `McpPreferencePage`**
   - Die Mode-Auswahl muss `agent-mode` und Custom Agents getrennt anzeigen.
   - Beim Anzeigen der Tools muss der aktuelle Mode-Status zuerst gespeichert werden, bevor die UI neu aufgebaut wird.
   - Für explizite Custom-Agent-Toollisten darf die Checkbox-Auswahl nicht editierbar sein. Der Tree selbst muss aber
     aktiviert bleiben, damit Scrollbars, Expand/Collapse und Navigation funktionieren.
   - Bei nicht editierbaren Modes werden Checkbox-Klicks durch erneutes Laden des Mode-Status zurückgenommen.
   - Bei expliziten Toollisten darf `saveModeToolStatus(...)` nicht den UI-Zustand übernehmen, sondern muss die Auswahl
     erneut aus der Agent-Datei über den Resolver ableiten.
   - Server- und Toolnamen müssen als TreeItem-Daten (`serverName`, `toolName`) gespeichert werden. Nicht ausschließlich
     aus dem sichtbaren Text parsen, weil dort Statushinweise oder Beschreibungen enthalten sein können.
   - Beim Speichern wird `MCP_TOOLS_STATUS` nur für `agent-mode` als Legacy-Kompatibilität aktualisiert; die getrennten
     Mode-Status werden in `MCP_TOOLS_MODE_STATUS` persistiert.
   - Der frühere Pfad, der Agent-Markdown-Dateien nach Preference-Änderungen aktualisierte oder geöffnete Agent-Dateien
     refreshte, darf nicht zurückkehren. Preferences verändern keine `.agent.md`-Dateien.

9. **Java-Debugger-Schaltfläche**
   - Für Custom Agents entscheidet nicht `customMode.getTools()`, sondern der mode-spezifische Preference-Status, ob
     das Built-in-Tool `java_debugger` aktiv ist.
   - Wenn nur `agent-mode` `java_debugger=true` hat, darf ein Custom Agent daraus kein `true` ableiten.

10. **MCP-Allowlist-Robustheit**
    - `McpUtils.getMcpAllowList(...)` muss CLS-Fehler mit Meldungspräfix `Invalid allowlist format:` abfangen und in
      `null` umwandeln.
    - Die Prüfung muss auch gewrappte Ursachenketten berücksichtigen.
    - Unerwartete Fehler dürfen nicht verschluckt werden, sondern müssen weiterhin exceptional bleiben.
    - Diese Änderung fehlte in `agent-mcp-config-fix.md` und muss bei einer erneuten Umsetzung explizit berücksichtigt
      werden.

11. **Eclipse-Projekteinstellungen**
    - Die lokalen Änderungen fügen UTF-8-Projektencoding für das Root-Projekt und für
      `com.microsoft.copilot.eclipse.swtbot.test` hinzu.
    - Zusätzlich setzt `com.microsoft.copilot.eclipse.swtbot.test/.settings/org.eclipse.core.runtime.prefs` den
      Projekt-Line-Separator auf CRLF.
    - Prüfe bei einer erneuten Umsetzung, ob diese Dateien wirklich Teil des Fixes sein sollen. Falls ja, dokumentiere
      sie; falls nein, lasse sie bewusst weg.

## In `agent-mcp-config-fix.md` fehlende oder unklare Änderungsangaben

- `CustomChatModeManager`-Generation gegen Out-of-order-Async-Loads ist nicht beschrieben.
- `McpUtils`-Robustheit gegen ungültige MCP-Allowlist-Formate ist nicht beschrieben.
- Die neuen Eclipse-`.settings`-Dateien sind nicht beschrieben.
- Das Speichern von `serverName` und `toolName` als TreeItem-Daten in `McpPreferencePage` ist nicht explizit beschrieben.
- Die serielle Reload-Kette in `UserPreferenceService` (`chatModeReload`) ist nicht explizit beschrieben.
- Das tatsächliche „Warten“ auf Inventar ist kein blockierendes Wait/Timeout, sondern ein später erneut ausgeführter
  `null`-Guard. Formuliere das bei Dokumentation oder Tests präzise.
- `McpConfigService` initialisiert den gespeicherten Toolstatus erst nach Erfassung des MCP-Inventars; diese Reihenfolge
  ist prüfungsrelevant.
- Die Tests für `McpUtils` fehlen in der Testliste.
- Die Dokumentation erwähnt entfernte Markdown-Schreibpfade; im lokalen Diff ist kein Datei-Delete mehr sichtbar. Prüfe
  deshalb, ob der erneute Ausgangsstand diese Klasse noch enthält oder ob sie bereits vorher entfernt wurde.

## Mögliche Vereinfachungen für eine erneute Umsetzung

1. **Status-/Inventar-Konvertierung zentralisieren**
   - `LanguageServerSettingManager` und `McpPreferencePage` bauen sehr ähnliche Strukturen aus Built-in- und MCP-Tools.
   - Extrahiere nach Möglichkeit eine kleine Utility oder erweitere `CustomAgentToolStatusResolver`, damit Parsing,
     Kopieren und Defaulting nur einmal implementiert werden.

2. **Stabile interne Server-ID statt lokalisierter Built-in-Überschrift prüfen**
   - Der Built-in-Server wird aktuell mit `Messages.preferences_page_mcp_tools_builtin` als Map-Key gespeichert.
   - Das ist kompatibel mit dem bestehenden Code, aber potenziell anfällig gegenüber Lokalisierung. Falls möglich, einen
     stabilen internen Key verwenden und nur in der UI lokalisieren. Falls zu riskant, bewusst beibehalten.

3. **Mode-Selector robuster machen**
   - Die bestehende UI rekonstruiert Mode-IDs aus Texten der Form `workspace: displayName`.
   - Wenn möglich, pflege parallel eine Liste `modeOptionIds`, damit die Auswahl nicht von Displaynamen, Workspace-Namen
     oder Doppelpunkten im Namen abhängt.

4. **Asynchrone LSP-Updates ohne `join()` vereinfachen**
   - `updateMcpToolsStatus(...)` startet aktuell einen Hintergrundtask, der Futures per `join()` sequenziell abwartet.
   - Prüfe, ob `CompletableFuture.allOf(...)` oder eine sauber gekettete Future-Rückgabe möglich ist. Falls die Signatur
     `void` bleiben muss, dokumentiere den Grund.

5. **Explizite Inventarbereitschaft statt implizitem Re-Run**
   - Minimal ist der bestehende `null`-Guard ausreichend.
   - Eine robustere Variante wäre ein expliziter „Inventory ready“-Mechanismus, der bei später eintreffendem Inventar die
     Synchronisierung genau einmal nachzieht und klar testbar macht.

6. **Verwaiste Custom-Agent-Preferences aufräumen**
   - Der aktuelle Fix initialisiert und überschreibt existierende Modes, entfernt aber alte Einträge gelöschter Agents
     nicht systematisch.
   - Falls fachlich erwünscht, Cleanup ergänzen; andernfalls bewusst nicht ändern, um keine User-Auswahl zu verlieren.

7. **Defensive Null-Checks ergänzen**
   - Einige neue Aufrufe auf `CopilotUi.getPlugin().getLanguageServerSettingManager()` setzen dessen Existenz voraus.
   - Prüfe frühe Startup-Pfade und füge bei Bedarf Null-Checks mit Logging hinzu.

## Akzeptanzkriterien

- Custom Agent mit fehlendem `tools`-Eintrag erhält beim ersten vollständigen Inventar alle verfügbaren Built-in- und
  MCP-Tools aktiviert, sofern noch kein eigener Preference-Eintrag existiert.
- Custom Agent mit bestehendem eigenen Preference-Eintrag behält diesen Eintrag, solange `tools` fehlt.
- Custom Agent mit `tools: []` deaktiviert alle verfügbaren Tools.
- Custom Agent mit expliziter Liste aktiviert genau die genannten Tools; alle anderen verfügbaren Tools sind deaktiviert.
- MCP-Servernamen mit Slash werden korrekt am letzten Slash getrennt.
- `agent-mode` und mehrere Custom Agents bleiben vollständig isoliert.
- `performOk()` sendet jeden Mode mit seinem eigenen Status an den Language Server.
- `customChatModeId` wird bei Custom Agents an MCP- und Built-in-Toolstatus-Endpunkte gesendet, bei `agent-mode` aber
  nicht gesetzt.
- Die Toolauswahl bleibt aus dem Chat für Custom Agents erreichbar.
- Für Custom Agents mit expliziter `tools`-Liste sind nur Checkbox-Änderungen blockiert; Tree und Scrollbars bleiben
  bedienbar.
- Preference-Änderungen schreiben keine `.agent.md`-Dateien.
- `java_debugger` für Custom Agents wird aus `MCP_TOOLS_MODE_STATUS` gelesen und erbt nicht von `agent-mode`.
- Ungültige MCP-Allowlist-Formate liefern `null`; unerwartete Allowlist-Fehler bleiben exceptional.

## Testfälle, die vorhanden sein oder ergänzt werden sollen

- `CustomAgentToolStatusResolverTest`
  - fehlende Toolliste initialisiert alle verfügbaren Tools ohne globale Vererbung
  - explizite Liste aktiviert exakt die angegebenen Tools
  - bestehende Preferences bleiben bei fehlender Toolliste maßgeblich
  - explizite leere Liste deaktiviert alle verfügbaren Tools
  - mehrere Custom Agents bleiben isoliert
  - Slash-Parsing mit Servernamen, die Slash enthalten
  - fehlendes Inventar verhindert die Initialisierung eines neuen Agents ohne `tools`
  - `allowsToolConfiguration()` bleibt für alle Custom Agents `true`
  - Checkbox-Editierbarkeit ist nur ohne explizite Toolliste gegeben

- `LanguageServerSettingManagerTests`
  - Custom-Agent-ID wird an MCP- und Conversation-Toolstatus gesendet
  - mehrere gespeicherte Modes werden beim Start mit jeweils eigenem Status initialisiert
  - explizite Tools überschreiben nur den betroffenen Custom-Agent-Status
  - fehlende Tools überschreiben bestehende Custom-Agent-Preferences nicht
  - Initialisierung wartet bis Built-in- und MCP-Inventar bekannt sind
  - `isBuiltInToolEnabledForMode` erbt nicht von `agent-mode`
  - `agent-mode` sendet Conversation-Status ohne `customChatModeId`

- `McpUtilsTests`
  - ungültiges Allowlist-Format direkt und gewrappt liefert `null`
  - erfolgreiche Antwort wird unverändert zurückgegeben
  - unerwarteter Fehler bleibt exceptional

- Falls möglich zusätzlich UI- oder SWTBot-Abdeckung für `McpPreferencePage`
  - Tree bleibt scrollbar/expandierbar, obwohl Checkbox-Änderungen bei expliziten Tools zurückgenommen werden
  - Mode-Wechsel speichert vorherigen Status und lädt den neuen Mode-Status korrekt
  - Auswahl von Custom Agents mit gleichen Displaynamen in verschiedenen Workspaces bleibt eindeutig

## Empfohlene Verifikation

Vor Abschluss mindestens ausführen:

```powershell
.\mvnw checkstyle:check
.\mvnw test
.\mvnw clean verify
```

Zusätzlich sinnvoll für schnelle Iteration:

```powershell
.\mvnw -pl com.microsoft.copilot.eclipse.ui.test -am test
```

Wenn Dateien außerhalb von Eclipse geändert werden, die betroffenen Projekte/Ordner refreshen. Bei Markdown- und
Properties-Dateien UTF-8 und vorhandene Zeilenenden beachten; für neue Markdown-Dateien UTF-8 ohne BOM und finales
Zeilenende verwenden.
