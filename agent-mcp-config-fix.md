# Branch `agent-mcp-config-fix`

## Vergleichsbasis

Der Branch wurde mit dem lokalen Standardbranch `main` verglichen. Ein Branch namens `master` ist in diesem
Repository nicht vorhanden; `origin/HEAD` verweist auf `origin/main`.

Enthaltene Branch-Commits vor der abschließenden Korrektur:

- `fada2d74` – Korrektur der referenzierten Target-Namen
- `16d1dcf6` – erster Fixversuch für die Custom-Agent-MCP-Konfiguration
- `8f39b674` – Platzhalter für diese Änderungsdokumentation

## Fachliches Verhalten

Die Toolauswahl für Custom Agents folgt nun durchgängig diesen Regeln:

1. Jeder Custom Agent bezieht seine Toolauswahl ausschließlich aus seinem eigenen, über die Mode-ID adressierten
   Eintrag in `MCP_TOOLS_MODE_STATUS`. Ein Custom Agent erbt niemals die globale Auswahl von `agent-mode`.
2. Enthält eine `.agent.md`-Datei einen `tools`-Eintrag, werden immer genau die dort genannten Tools angewendet und in
   den agentenspezifischen Preferences abgebildet. Dies gilt bei jedem Laden und nach jeder Dateiänderung;
   `tools: []` deaktiviert somit alle Tools.
3. Fehlt der `tools`-Eintrag und existiert für den Custom Agent noch kein Preference-Eintrag, werden einmalig alle
   aktuell verfügbaren Built-in- und MCP-Tools aktiviert. Danach ist ausschließlich dieser agentenspezifische
   Preference-Eintrag maßgeblich. Später hinzukommende Tools werden nicht automatisch aktiviert.
4. Änderungen in den Preferences verändern keine `.agent.md`-Datei. Beim Übernehmen wird die Auswahl jedes Modes
   unter seiner eigenen ID gespeichert und mit dieser ID an den Language Server übertragen.
5. Die Tool Auswahl soll auch weiterhin für custom agents aus dem Chat fenster zu öffnen sein.
6. Die Scrollbar in der Toolauswahl für custom agents darf nicht disabled werden, nur die checkboxen sollen disabled sein wenn der custom agent einen tools eintrag hat.

## Implementierungsänderungen

### Custom-Agent-Modell und Auflösung

- `CustomChatMode` unterscheidet zwischen einem fehlenden und einem explizit vorhandenen `tools`-Eintrag.
- `CustomAgentToolStatusResolver` wendet explizite Toollisten bei jeder Synchronisierung an und setzt alle nicht
  genannten verfügbaren Tools auf deaktiviert.
- Ohne `tools` bleibt ein vorhandener Preference-Status erhalten; ein neuer Agent startet mit allen Tools aktiviert,
  sobald das vollständige Inventar der Built-in- und MCP-Tools bekannt ist.
- Eine explizit leere Liste überschreibt die Preferences mit einer vollständig deaktivierten Toolauswahl.
- Servernamen mit `/` werden am letzten Slash vom Toolnamen getrennt.

### Preferences und Language Server

- Die „Configure Tools“-Action im Chat bleibt für jeden Custom Agent sichtbar, unabhängig davon, ob dessen Toolauswahl
  in den Preferences editierbar oder durch die Agent-Datei vorgegeben ist.
- `McpPreferencePage` lässt die Toolauswahl für den Built-in Agent und Custom Agents ohne `tools`-Eintrag bearbeiten.
  Bei einer expliziten Toolliste wird die dateibasierte Auswahl angezeigt, aber nicht zur Bearbeitung freigegeben. Der
  Toolbaum bleibt aktiviert, damit Auf- und Zuklappen sowie die Scrollbars weiterhin funktionieren; nur Änderungen an
  den Checkboxen werden verhindert.
- Fehlt ein globaler `agent-mode`-Eintrag, wird die bestehende Auswahl aus dem Legacy-Key `MCP_TOOLS_STATUS`
  übernommen.
- `MCP_TOOLS_MODE_STATUS` persistiert die getrennten Einträge von `agent-mode` und jedem Custom Agent.
- Beim Bestätigen der Preferences wird jeder Mode mit genau seinem eigenen Status an den Language Server übertragen.
- `LanguageServerSettingManager` versieht Updates für Custom Agents an beiden Toolstatus-Endpunkten mit deren
  `file://`-Mode-ID und stellt beim Start alle gespeicherten Mode-Einträge wieder her.
- Der im ersten Fixversuch hinzugefügte `CustomAgentToolFileUpdater` wurde vollständig entfernt. Damit gibt es keinen
  Preference-Schreibpfad mehr in Agent-Markdown-Dateien.

### Aktualisierung nach Dateiänderungen

- `UserPreferenceService` reagiert auf `CustomizationType.AGENT` und lädt Custom Modes erneut.
- Bei jedem Laden und nach einem Agent-Dateiänderungsereignis werden explizite `tools`-Einträge nach dem asynchronen
  Reload in den agentenspezifischen Preferences persistiert.
- Built-in- und MCP-Toolinventar werden getrennt erfasst. Die Erstinitialisierung eines Agents ohne `tools` wartet, bis
  beide Inventarquellen bekannt sind, damit kein vorzeitiger leerer Preference-Eintrag entsteht.
- Die Preference-Seite synchronisiert ihre Mode-Auswahl mit den frisch geladenen Mode-Daten und wendet dieselbe
  Prioritätsregel an.
- Die Java-Debugger-Schaltfläche übernimmt für Custom Agents keinen Status des globalen Agent-Modus.

### Tests

- `CustomAgentToolStatusResolverTest` deckt die anfängliche Aktivierung aller Tools, bestehende Preferences, explizite
  und explizit leere `tools`-Einträge, die Isolation mehrerer Agents, MCP-Servernamen mit Slash sowie die getrennten
  Regeln für den Zugriff auf die Toolkonfiguration und die Editierbarkeit der Checkboxen ab.
- `LanguageServerSettingManagerTests` stellt sicher, dass Custom-Agent-IDs an den MCP- und den
  Conversation-Status-Endpunkt übertragen und mehrere gespeicherte Modes getrennt initialisiert werden.
- Die Tests des entfernten Markdown-Schreibers wurden entfernt.

### Bereits im Branch enthaltene Target-Anpassungen

- `target-terminal.target` und `target-tm-terminal.target` referenzieren `base.target` über den korrekten
  Projektnamen `copilot-for-eclipse` statt `github-copilot-for-eclipse`.
- `target-terminal.target` enthält zusätzlich rein formatierende XML-Anpassungen.

## Verifikation

- Eclipse Clean/Full Build der Projekte `com.microsoft.copilot.eclipse.core`,
  `com.microsoft.copilot.eclipse.ui` und `com.microsoft.copilot.eclipse.ui.test`: keine Fehler.
- `./mvnw checkstyle:check`: erfolgreich, keine Checkstyle-Verstöße.
- `./mvnw test`: erfolgreich.
- `./mvnw clean verify`: erfolgreich.
  - Core-Tests: 10.210 Tests, 0 Fehler
    - UI-Tests: 496 Tests, 0 Fehler
  - UI-Jobs-Tests: 4 Tests, 0 Fehler
  - Die SWTBot-Probe-Suite ist im Standard-Build konfigurationsgemäß übersprungen worden.
