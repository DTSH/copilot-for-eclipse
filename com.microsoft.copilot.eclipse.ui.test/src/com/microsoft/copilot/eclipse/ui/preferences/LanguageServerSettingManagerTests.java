// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.preferences;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.microsoft.copilot.eclipse.core.Constants;
import com.microsoft.copilot.eclipse.core.chat.CustomChatMode;
import com.microsoft.copilot.eclipse.core.lsp.CopilotLanguageServerConnection;
import com.microsoft.copilot.eclipse.core.lsp.mcp.McpServerToolsCollection;
import com.microsoft.copilot.eclipse.core.lsp.mcp.McpToolInformation;
import com.microsoft.copilot.eclipse.core.lsp.protocol.ConversationMode;
import com.microsoft.copilot.eclipse.core.lsp.protocol.CopilotLanguageServerSettings;
import com.microsoft.copilot.eclipse.core.lsp.protocol.CopilotLanguageServerSettings.CopilotSettings;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolInformation;
import com.microsoft.copilot.eclipse.core.lsp.protocol.UpdateConversationToolsStatusParams;
import com.microsoft.copilot.eclipse.core.lsp.protocol.UpdateMcpToolsStatusParams;
import com.microsoft.copilot.eclipse.core.utils.GsonUtils;
import com.microsoft.copilot.eclipse.core.utils.PlatformUtils;
import com.microsoft.copilot.eclipse.ui.CopilotUi;
import com.microsoft.copilot.eclipse.ui.utils.PreferencesUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LanguageServerSettingManagerTests {
  @Mock
  private IPreferenceStore mockPreferenceStore;

  @Mock
  private CopilotLanguageServerConnection mockLsConnection;

  @Mock
  private IProxyService mockProxyService;

  @Test
  void testNoProxy() {
    // when no proxy is applicable
    // arrange
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);
    var params = new DidChangeConfigurationParams();
    var settings = new CopilotLanguageServerSettings();
    settings.getGithubSettings().getCopilotSettings().getAgent()
        .setEnableSkills(PreferencesUtils.isSkillsEnabled())
        .setTranscriptDirectory(PlatformUtils.getTranscriptDirectory());
    settings.getGithubSettings().getCopilotSettings().getAgent().setAutoCompress(true);
    settings.getGithubSettings().getCopilotSettings().getAgent()
        .getTools().getTerminal().setAutoApprove(new LinkedHashMap<>());
    settings.getGithubSettings().getCopilotSettings().getAgent()
        .getTools().getEdit().setAutoApprove(new LinkedHashMap<>());
    params.setSettings(settings);

    // act
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);
    manager.updateProxySettings();
    manager.syncConfiguration();

    // assert
    verify(mockLsConnection, times(1)).updateConfig(params);
  }

  @Test
  void testSyncConfigurationIncludesEmptyMcpAutoApproveList() {
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);

    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);
    manager.syncConfiguration();

    ArgumentCaptor<DidChangeConfigurationParams> paramsCaptor = ArgumentCaptor
        .forClass(DidChangeConfigurationParams.class);
    verify(mockLsConnection).updateConfig(paramsCaptor.capture());

    JsonObject serializedSettings = GsonUtils.getDefault().toJsonTree(paramsCaptor.getValue().getSettings())
        .getAsJsonObject();
    assertTrue(serializedSettings.getAsJsonObject("github")
        .getAsJsonObject("copilot")
        .getAsJsonObject("agent")
        .getAsJsonObject("tools")
        .getAsJsonObject("mcp")
        .getAsJsonArray("autoApprove")
        .isEmpty());
  }

  @Test
  void testBasicProxy() {
    // basic proxy test
    // arrange
    IProxyData mockProxyData = mock(IProxyData.class);
    when(mockProxyData.getHost()).thenReturn("localhost");
    when(mockProxyData.getPort()).thenReturn(8080);
    when(mockProxyData.getType()).thenReturn("HTTPS");
    when(mockProxyData.isRequiresAuthentication()).thenReturn(false);
    when(mockProxyService.select(any())).thenReturn(new IProxyData[] { mockProxyData });
    when(mockProxyService.isProxiesEnabled()).thenReturn(true);
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);
    var params = new DidChangeConfigurationParams();
    var settings = new CopilotLanguageServerSettings();
    settings.getHttp().setProxy("HTTPS://localhost:8080");
    settings.getGithubSettings().getCopilotSettings().getAgent()
        .setEnableSkills(PreferencesUtils.isSkillsEnabled())
        .setTranscriptDirectory(PlatformUtils.getTranscriptDirectory());
    settings.getGithubSettings().getCopilotSettings().getAgent().setAutoCompress(true);
    settings.getGithubSettings().getCopilotSettings().getAgent()
        .getTools().getTerminal().setAutoApprove(new LinkedHashMap<>());
    settings.getGithubSettings().getCopilotSettings().getAgent()
        .getTools().getEdit().setAutoApprove(new LinkedHashMap<>());
    params.setSettings(settings);

    // act
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);
    manager.updateProxySettings();
    manager.syncConfiguration();

    // assert
    verify(mockLsConnection, times(1)).updateConfig(params);
  }

  private void setupWorkspaceInstructionsMocks(boolean enabled, String instructions) {
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);
    when(mockPreferenceStore.getBoolean(Constants.ENABLE_STRICT_SSL)).thenReturn(false);
    when(mockPreferenceStore.getString(Constants.PROXY_KERBEROS_SP)).thenReturn(null);
    when(mockPreferenceStore.getString(Constants.GITHUB_ENTERPRISE)).thenReturn(null);
    when(mockPreferenceStore.getBoolean(Constants.CUSTOM_INSTRUCTIONS_WORKSPACE_ENABLED)).thenReturn(enabled);
    if (enabled && instructions != null) {
      when(mockPreferenceStore.getString(Constants.CUSTOM_INSTRUCTIONS_WORKSPACE)).thenReturn(instructions);
    }
  }

  @Test
  void testUpdateConfigShouldBeCalledWhenWorkspaceInstructionsEnabledWithContent() {
    // arrange
    IProxyService mockProxyService = mock(IProxyService.class);
    CopilotLanguageServerConnection mockLsConnection = mock(CopilotLanguageServerConnection.class);
    setupWorkspaceInstructionsMocks(true, "Test instructions");

    DidChangeConfigurationParams params = new DidChangeConfigurationParams();
    CopilotSettings copilotSettings = new CopilotSettings();
    copilotSettings.setWorkspaceCopilotInstructions("Test instructions");
    copilotSettings.getAgent().setEnableSkills(PreferencesUtils.isSkillsEnabled());
    copilotSettings.getAgent().setAutoCompress(true);
    CopilotLanguageServerSettings settings = new CopilotLanguageServerSettings();
    settings.getGithubSettings().setCopilotSettings(copilotSettings);
    settings.getGithubSettings().getCopilotSettings().getAgent()
        .setTranscriptDirectory(PlatformUtils.getTranscriptDirectory());
    settings.getGithubSettings().getCopilotSettings().getAgent()
        .getTools().getTerminal().setAutoApprove(new LinkedHashMap<>());
    settings.getGithubSettings().getCopilotSettings().getAgent()
        .getTools().getEdit().setAutoApprove(new LinkedHashMap<>());
    params.setSettings(settings);

    // act
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);
    manager.updateProxySettings();
    manager.syncConfiguration();

    // assert
    verify(mockPreferenceStore, times(1)).getString(Constants.CUSTOM_INSTRUCTIONS_WORKSPACE);
    verify(mockLsConnection, times(1)).updateConfig(params);

    CopilotSettings capturedSettings = ((CopilotLanguageServerSettings) params.getSettings()).getGithubSettings()
        .getCopilotSettings();
    assertEquals("Test instructions", capturedSettings.getWorkspaceCopilotInstructions());
    assertNull(capturedSettings.getMcpServers(), "Custom instructions update should not set MCP servers");
  }

  @Test
  void testUpdateConfigShouldBeCalledWithoutInstructionWhenWorkspaceInstructionsDisabled() {
    // arrange
    IProxyService mockProxyService = mock(IProxyService.class);
    CopilotLanguageServerConnection mockLsConnection = mock(CopilotLanguageServerConnection.class);
    setupWorkspaceInstructionsMocks(false, null);

    // Expected params should have empty workspace instructions since it's disabled
    DidChangeConfigurationParams expectedParams = new DidChangeConfigurationParams();
    CopilotLanguageServerSettings expectedSettings = new CopilotLanguageServerSettings();
    expectedSettings.getGithubSettings().getCopilotSettings().getAgent()
        .setEnableSkills(PreferencesUtils.isSkillsEnabled())
        .setTranscriptDirectory(PlatformUtils.getTranscriptDirectory());
    expectedSettings.getGithubSettings().getCopilotSettings().getAgent().setAutoCompress(true);
    expectedSettings.getGithubSettings().getCopilotSettings().getAgent()
        .getTools().getTerminal().setAutoApprove(new LinkedHashMap<>());
    expectedSettings.getGithubSettings().getCopilotSettings().getAgent()
        .getTools().getEdit().setAutoApprove(new LinkedHashMap<>());
    expectedParams.setSettings(expectedSettings);

    // act
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);
    manager.updateProxySettings();
    manager.syncConfiguration();

    // assert - verify that updateConfig is called with settings that have empty workspace instructions
    verify(mockPreferenceStore, times(0)).getString(Constants.CUSTOM_INSTRUCTIONS_WORKSPACE);
    verify(mockLsConnection, times(1)).updateConfig(expectedParams);

    CopilotSettings capturedSettings = ((CopilotLanguageServerSettings) expectedParams.getSettings())
        .getGithubSettings().getCopilotSettings();
    assertNull(capturedSettings.getWorkspaceCopilotInstructions());
    assertNull(capturedSettings.getMcpServers(), "Custom instructions update should not set MCP servers");
  }

  @Test
  void testUpdateAutoCompletionSetting() {
    IPreferenceStore preferenceStore = CopilotUi.getPlugin().getPreferenceStore();

    new LanguageServerSettingManager(mockLsConnection, mockProxyService, preferenceStore);
    ArgumentCaptor<DidChangeConfigurationParams> paramsCaptor = ArgumentCaptor
        .forClass(DidChangeConfigurationParams.class);

    preferenceStore.setValue(Constants.AUTO_SHOW_COMPLETION, false);

    verify(mockLsConnection, timeout(100).times(1)).updateConfig(paramsCaptor.capture());

    CopilotLanguageServerSettings capturedSettings = (CopilotLanguageServerSettings) paramsCaptor.getValue()
        .getSettings();
    assertFalse(capturedSettings.isEnableAutoCompletions());
  }

  @Test
  void testUpdateStrictSslSetting() {
    IPreferenceStore preferenceStore = CopilotUi.getPlugin().getPreferenceStore();

    new LanguageServerSettingManager(mockLsConnection, mockProxyService, preferenceStore);
    ArgumentCaptor<DidChangeConfigurationParams> paramsCaptor = ArgumentCaptor
        .forClass(DidChangeConfigurationParams.class);

    preferenceStore.setValue(Constants.ENABLE_STRICT_SSL, false);

    verify(mockLsConnection, timeout(100).times(1)).updateConfig(paramsCaptor.capture());

    CopilotLanguageServerSettings capturedSettings = (CopilotLanguageServerSettings) paramsCaptor.getValue()
        .getSettings();
    assertFalse(capturedSettings.getHttp().isProxyStrictSsl());
  }

  @Test
  void testInitializeMcpToolsStatusWhenEmpty() {
    // arrange
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);
    when(mockPreferenceStore.getString(Constants.PROXY_KERBEROS_SP)).thenReturn(null);
    when(mockPreferenceStore.getString(Constants.GITHUB_ENTERPRISE)).thenReturn(null);
    when(mockPreferenceStore.getString(Constants.CUSTOM_INSTRUCTIONS_GIT_COMMIT)).thenReturn(null);
    when(mockPreferenceStore.getString(Constants.MCP_TOOLS_MODE_STATUS)).thenReturn("");
    when(mockPreferenceStore.getString(Constants.MCP_TOOLS_STATUS)).thenReturn("");

    // act
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);
    assertDoesNotThrow(() -> manager.initializeMcpToolsStatus());

    // assert
    verify(mockPreferenceStore, times(1)).getString(Constants.MCP_TOOLS_MODE_STATUS);
    verify(mockPreferenceStore, times(1)).getString(Constants.MCP_TOOLS_STATUS);
    verify(mockLsConnection, times(0)).updateMcpToolsStatus(any());
  }

  @Test
  void testUpdateToolStatusForMode_customAgentSendsModeIdToAllStatusUpdates() {
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);
    when(mockLsConnection.updateMcpToolsStatus(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(mockLsConnection.updateConversationToolsStatus(any()))
        .thenReturn(CompletableFuture.completedFuture(new Object()));
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);
    String customModeId = "file:///C:/workspace/.github/agents/test.agent.md";
    String toolStatusJson = "{\"Built-in Tools\":{\"file_search\":false},"
        + "\"custom-mcp\":{\"file_search\":true}}";

    manager.updateToolStatusForMode(toolStatusJson, customModeId);

    ArgumentCaptor<UpdateMcpToolsStatusParams> mcpParamsCaptor = ArgumentCaptor
        .forClass(UpdateMcpToolsStatusParams.class);
    verify(mockLsConnection).updateMcpToolsStatus(mcpParamsCaptor.capture());
    assertEquals(customModeId, mcpParamsCaptor.getValue().getCustomChatModeId());

    ArgumentCaptor<UpdateConversationToolsStatusParams> conversationParamsCaptor = ArgumentCaptor
        .forClass(UpdateConversationToolsStatusParams.class);
    verify(mockLsConnection).updateConversationToolsStatus(conversationParamsCaptor.capture());
    assertEquals(customModeId, conversationParamsCaptor.getValue().getCustomChatModeId());
    assertEquals("disabled", conversationParamsCaptor.getValue().getTools().get(0).getStatus());
  }

  @Test
  void testInitializeMcpToolsStatus_multipleModesUseTheirOwnPreferences() {
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);
    when(mockLsConnection.updateConversationToolsStatus(any()))
        .thenReturn(CompletableFuture.completedFuture(new Object()));
    String firstCustomModeId = "file:///C:/workspace/.github/agents/first.agent.md";
    String secondCustomModeId = "file:///C:/workspace/.github/agents/second.agent.md";
    String modeToolStatusJson = "{\"agent-mode\":{\"Built-in Tools\":{\"java_debugger\":true}},"
        + "\"" + firstCustomModeId + "\":{\"Built-in Tools\":{\"java_debugger\":false}},"
        + "\"" + secondCustomModeId + "\":{\"Built-in Tools\":{\"java_debugger\":true}}}";
    when(mockPreferenceStore.getString(Constants.MCP_TOOLS_MODE_STATUS)).thenReturn(modeToolStatusJson);
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);

    manager.initializeMcpToolsStatus();

    ArgumentCaptor<UpdateConversationToolsStatusParams> paramsCaptor = ArgumentCaptor
        .forClass(UpdateConversationToolsStatusParams.class);
    verify(mockLsConnection, times(3)).updateConversationToolsStatus(paramsCaptor.capture());
    UpdateConversationToolsStatusParams firstCustomParams = paramsCaptor.getAllValues().stream()
        .filter(params -> firstCustomModeId.equals(params.getCustomChatModeId()))
        .findFirst()
        .orElseThrow();
    UpdateConversationToolsStatusParams secondCustomParams = paramsCaptor.getAllValues().stream()
        .filter(params -> secondCustomModeId.equals(params.getCustomChatModeId()))
        .findFirst()
        .orElseThrow();
    assertEquals("disabled", firstCustomParams.getTools().get(0).getStatus());
    assertEquals("enabled", secondCustomParams.getTools().get(0).getStatus());
  }

  @Test
  void testSynchronizeCustomAgentToolPreferences_explicitToolsReplaceOnlyCustomAgentStatus() {
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);
    String customModeId = "file:///C:/workspace/.github/agents/test.agent.md";
    String modeToolStatusJson = "{\"agent-mode\":{\"Built-in Tools\":{\"java_debugger\":true}},"
        + "\"" + customModeId + "\":{\"Built-in Tools\":{\"read_file\":false}}}";
    when(mockPreferenceStore.getString(Constants.MCP_TOOLS_MODE_STATUS)).thenReturn(modeToolStatusJson);
    ConversationMode conversationMode = new ConversationMode();
    conversationMode.setId(customModeId);
    conversationMode.setName("Test Agent");
    conversationMode.setCustomTools(List.of("run_in_terminal"));
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);
    manager.updateAvailableBuiltInTools(List.of(createTool("java_debugger"), createTool("run_in_terminal")));
    manager.updateAvailableMcpTools(List.of(createMcpServer("custom-mcp", "search")));

    manager.synchronizeCustomAgentToolPreferences(List.of(new CustomChatMode(conversationMode)));

    ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockPreferenceStore).setValue(eq(Constants.MCP_TOOLS_MODE_STATUS), statusCaptor.capture());
    Map<?, ?> persistedStatus = GsonUtils.getDefault().fromJson(statusCaptor.getValue(), Map.class);
    assertEquals(Map.of("Built-in Tools", Map.of("java_debugger", true)), persistedStatus.get("agent-mode"));
    assertEquals(Map.of(
        "Built-in Tools", Map.of("java_debugger", false, "run_in_terminal", true),
        "custom-mcp", Map.of("search", false)), persistedStatus.get(customModeId));
  }

  @Test
  void testSynchronizeCustomAgentToolPreferences_missingToolsExistingStatusIsNotOverwritten() {
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);
    String customModeId = "file:///C:/workspace/.github/agents/test.agent.md";
    String modeToolStatusJson = "{\"" + customModeId
        + "\":{\"Built-in Tools\":{\"run_in_terminal\":true}}}";
    when(mockPreferenceStore.getString(Constants.MCP_TOOLS_MODE_STATUS)).thenReturn(modeToolStatusJson);
    ConversationMode conversationMode = new ConversationMode();
    conversationMode.setId(customModeId);
    conversationMode.setName("Test Agent");
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);
    manager.updateAvailableBuiltInTools(List.of(createTool("read_file"), createTool("run_in_terminal")));
    manager.updateAvailableMcpTools(List.of());

    manager.synchronizeCustomAgentToolPreferences(List.of(new CustomChatMode(conversationMode)));

    verify(mockPreferenceStore, times(0)).setValue(eq(Constants.MCP_TOOLS_MODE_STATUS), any(String.class));
  }

  @Test
  void testSynchronizeCustomAgentToolPreferences_missingToolsWaitsThenEnablesCompleteInventory() {
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);
    when(mockPreferenceStore.getString(Constants.MCP_TOOLS_MODE_STATUS)).thenReturn("");
    String customModeId = "file:///C:/workspace/.github/agents/test.agent.md";
    ConversationMode conversationMode = new ConversationMode();
    conversationMode.setId(customModeId);
    conversationMode.setName("Test Agent");
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);

    manager.synchronizeCustomAgentToolPreferences(List.of(new CustomChatMode(conversationMode)));
    manager.updateAvailableBuiltInTools(List.of(createTool("read_file")));

    verify(mockPreferenceStore, times(0)).setValue(eq(Constants.MCP_TOOLS_MODE_STATUS), any(String.class));

    manager.updateAvailableMcpTools(List.of(createMcpServer("custom-mcp", "search")));

    ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockPreferenceStore).setValue(eq(Constants.MCP_TOOLS_MODE_STATUS), statusCaptor.capture());
    Map<?, ?> persistedStatus = GsonUtils.getDefault().fromJson(statusCaptor.getValue(), Map.class);
    assertEquals(Map.of(
        "Built-in Tools", Map.of("read_file", true),
        "custom-mcp", Map.of("search", true)), persistedStatus.get(customModeId));
  }

  @Test
  void testIsBuiltInToolEnabledForMode_customAgentDoesNotInheritAgentModePreference() {
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);
    String customModeId = "file:///C:/workspace/.github/agents/test.agent.md";
    String modeToolStatusJson = "{\"agent-mode\":{\"Built-in Tools\":{\"java_debugger\":true}}}";
    when(mockPreferenceStore.getString(Constants.MCP_TOOLS_MODE_STATUS)).thenReturn(modeToolStatusJson);
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);

    boolean customAgentEnabled = manager.isBuiltInToolEnabledForMode(customModeId, "java_debugger");

    assertFalse(customAgentEnabled);
  }

  @Test
  void testUpdateToolStatusForMode_agentModeSendsBuiltInConversationStatus() {
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);
    when(mockLsConnection.updateMcpToolsStatus(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(mockLsConnection.updateConversationToolsStatus(any()))
        .thenReturn(CompletableFuture.completedFuture(new Object()));
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);

    manager.updateToolStatusForMode("{\"Built-in Tools\":{\"file_search\":false}}", "agent-mode");

    verify(mockLsConnection, times(0)).updateMcpToolsStatus(any(UpdateMcpToolsStatusParams.class));
    ArgumentCaptor<UpdateConversationToolsStatusParams> paramsCaptor = ArgumentCaptor
        .forClass(UpdateConversationToolsStatusParams.class);
    verify(mockLsConnection).updateConversationToolsStatus(paramsCaptor.capture());

    UpdateConversationToolsStatusParams params = paramsCaptor.getValue();
    assertEquals("Agent", params.getChatModeKind());
    assertNull(params.getCustomChatModeId());
    assertEquals(1, params.getTools().size());
    assertEquals("file_search", params.getTools().get(0).getName());
    assertEquals("disabled", params.getTools().get(0).getStatus());
  }

  @Test
  void testProxyWithNoProxyHosts() {
    // Verifies noProxy bypass list is transmitted when proxy is configured
    // This fixes the issue where internal MCP servers couldn't bypass proxy

    // arrange
    IProxyData mockProxyData = mock(IProxyData.class);
    when(mockProxyData.getHost()).thenReturn("proxy.com");
    when(mockProxyData.getPort()).thenReturn(8080);
    when(mockProxyData.getType()).thenReturn("HTTP");
    when(mockProxyData.isRequiresAuthentication()).thenReturn(false);

    String[] noProxyHosts = new String[] { "localhost", "*.internal.net" };
    when(mockProxyService.select(any())).thenReturn(new IProxyData[] { mockProxyData });
    when(mockProxyService.isProxiesEnabled()).thenReturn(true);
    when(mockProxyService.getNonProxiedHosts()).thenReturn(noProxyHosts);
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);

    // act
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);
    manager.updateProxySettings();

    // assert
    CopilotLanguageServerSettings settings = manager.getSettings();
    assertEquals("HTTP://proxy.com:8080", settings.getHttp().getProxy());
    assertEquals(2, settings.getHttp().getNoProxy().length);
    assertEquals("localhost", settings.getHttp().getNoProxy()[0]);
  }

  @Test
  void testUpdateProxySettingsWithProxyAndAuth() {
    // Test updateProxySettings() method to verify both proxy and auth are set correctly
    // arrange
    IProxyData mockProxyData = mock(IProxyData.class);
    when(mockProxyData.getHost()).thenReturn("proxy.example.com");
    when(mockProxyData.getPort()).thenReturn(3128);
    when(mockProxyData.getType()).thenReturn("HTTPS");
    when(mockProxyData.isRequiresAuthentication()).thenReturn(true);
    when(mockProxyData.getUserId()).thenReturn("testuser");
    when(mockProxyData.getPassword()).thenReturn("testpass");

    when(mockProxyService.select(any())).thenReturn(new IProxyData[] { mockProxyData });
    when(mockProxyService.isProxiesEnabled()).thenReturn(true);
    when(mockPreferenceStore.getBoolean(Constants.AUTO_SHOW_COMPLETION)).thenReturn(true);

    // act
    LanguageServerSettingManager manager = new LanguageServerSettingManager(mockLsConnection, mockProxyService,
        mockPreferenceStore);
    manager.updateProxySettings();

    // assert
    CopilotLanguageServerSettings settings = manager.getSettings();
    assertEquals("HTTPS://proxy.example.com:3128", settings.getHttp().getProxy());
    assertEquals("testuser:testpass", settings.getHttp().getProxyAuthorization());
  }

  private LanguageModelToolInformation createTool(String name) {
    LanguageModelToolInformation tool = new LanguageModelToolInformation();
    tool.setName(name);
    return tool;
  }

  private McpServerToolsCollection createMcpServer(String serverName, String... toolNames) {
    McpServerToolsCollection server = new McpServerToolsCollection();
    server.setName(serverName);
    server.setTools(List.of(toolNames).stream().map(name -> {
      McpToolInformation tool = new McpToolInformation();
      tool.setName(name);
      return tool;
    }).toList());
    return server;
  }
}