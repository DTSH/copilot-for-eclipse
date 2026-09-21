// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.preferences;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.copilot.eclipse.core.chat.CustomChatMode;
import com.microsoft.copilot.eclipse.core.lsp.mcp.McpServerToolsCollection;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolInformation;

/**
 * Resolves per-mode tool status for custom agents from agent definitions, existing preferences and tool inventory.
 */
public class CustomAgentToolStatusResolver {
  public static final String AGENT_MODE_ID = "agent-mode";
  public static final String CUSTOM_MODE_ID_PREFIX = "file://";

  private final String builtInToolsServerName;

  /**
   * Creates a resolver.
   *
   * @param builtInToolsServerName the server key used for built-in tools in preferences
   */
  public CustomAgentToolStatusResolver(String builtInToolsServerName) {
    this.builtInToolsServerName = builtInToolsServerName;
  }

  /**
   * Resolves the complete mode status map without inheriting agent-mode status for custom modes.
   *
   * @param customModes the custom modes to synchronize
   * @param existingModeStatus existing mode status from preferences
   * @param availableBuiltInTools current built-in tool inventory, or null when not yet known
   * @param availableMcpTools current MCP tool inventory, or null when not yet known
   * @return a deep-copied status map with synchronized custom-agent entries
   */
  public Map<String, Map<String, Map<String, Boolean>>> resolveCustomAgentToolStatus(
      List<CustomChatMode> customModes,
      Map<String, Map<String, Map<String, Boolean>>> existingModeStatus,
      List<LanguageModelToolInformation> availableBuiltInTools,
      List<McpServerToolsCollection> availableMcpTools) {
    Map<String, Map<String, Map<String, Boolean>>> resolved = deepCopyModeStatus(existingModeStatus);
    if (customModes == null) {
      return resolved;
    }

    for (CustomChatMode mode : customModes) {
      if (mode == null || StringUtils.isBlank(mode.getId())) {
        continue;
      }

      Map<String, Map<String, Boolean>> existingStatus = resolved.get(mode.getId());
      Map<String, Map<String, Boolean>> modeStatus = resolveModeToolStatus(
          mode, existingStatus, availableBuiltInTools, availableMcpTools);
      if (modeStatus != null) {
        resolved.put(mode.getId(), modeStatus);
      }
    }
    return resolved;
  }

  /**
   * Resolves a single custom mode status.
   *
   * @return resolved status, or null when a missing tools entry cannot be initialized because inventory is incomplete
   */
  public Map<String, Map<String, Boolean>> resolveModeToolStatus(CustomChatMode mode,
      Map<String, Map<String, Boolean>> existingStatus,
      List<LanguageModelToolInformation> availableBuiltInTools,
      List<McpServerToolsCollection> availableMcpTools) {
    if (mode == null) {
      return null;
    }

    boolean hasCompleteInventory = hasCompleteInventory(availableBuiltInTools, availableMcpTools);
    if (!mode.hasExplicitTools()) {
      if (existingStatus != null) {
        return deepCopyServerStatus(existingStatus);
      }
      if (!hasCompleteInventory) {
        return null;
      }
      return createInventoryStatus(availableBuiltInTools, availableMcpTools, true);
    }

    Map<String, Map<String, Boolean>> resolved = hasCompleteInventory
        ? createInventoryStatus(availableBuiltInTools, availableMcpTools, false)
        : new LinkedHashMap<>();
    for (String toolSpec : mode.getTools()) {
      ToolReference reference = parseToolReference(toolSpec);
      if (reference != null) {
        setToolStatus(resolved, reference.serverName, reference.toolName, true);
      }
    }
    return resolved;
  }

  /**
   * Determines whether checkboxes may be edited for the given custom mode.
   */
  public boolean isToolStatusEditable(CustomChatMode mode) {
    return mode == null || !mode.hasExplicitTools();
  }

  /**
   * Determines whether both inventory sources have been received.
   */
  public boolean hasCompleteInventory(List<LanguageModelToolInformation> availableBuiltInTools,
      List<McpServerToolsCollection> availableMcpTools) {
    return availableBuiltInTools != null && availableMcpTools != null;
  }

  /**
   * Determines whether a mode id belongs to a custom agent.
   */
  public static boolean isCustomModeId(String modeId) {
    return modeId != null && modeId.startsWith(CUSTOM_MODE_ID_PREFIX);
  }

  private Map<String, Map<String, Boolean>> createInventoryStatus(
      List<LanguageModelToolInformation> availableBuiltInTools,
      List<McpServerToolsCollection> availableMcpTools,
      boolean enabled) {
    Map<String, Map<String, Boolean>> status = new LinkedHashMap<>();
    addBuiltInTools(status, availableBuiltInTools, enabled);
    addMcpTools(status, availableMcpTools, enabled);
    return status;
  }

  private void addBuiltInTools(Map<String, Map<String, Boolean>> status,
      List<LanguageModelToolInformation> tools, boolean enabled) {
    if (tools == null) {
      return;
    }
    for (LanguageModelToolInformation tool : tools) {
      if (tool != null && StringUtils.isNotBlank(tool.getName())) {
        setToolStatus(status, builtInToolsServerName, tool.getName(), enabled);
      }
    }
  }

  private void addMcpTools(Map<String, Map<String, Boolean>> status,
      List<McpServerToolsCollection> servers, boolean enabled) {
    if (servers == null) {
      return;
    }
    for (McpServerToolsCollection server : servers) {
      if (server == null || StringUtils.isBlank(server.getName()) || server.getTools() == null) {
        continue;
      }
      for (LanguageModelToolInformation tool : server.getTools()) {
        if (tool != null && StringUtils.isNotBlank(tool.getName())) {
          setToolStatus(status, server.getName(), tool.getName(), enabled);
        }
      }
    }
  }

  private ToolReference parseToolReference(String toolSpec) {
    if (StringUtils.isBlank(toolSpec)) {
      return null;
    }

    String trimmedSpec = toolSpec.trim();
    int lastSlashIndex = trimmedSpec.lastIndexOf('/');
    if (lastSlashIndex < 0) {
      return StringUtils.isBlank(trimmedSpec) ? null : new ToolReference(builtInToolsServerName, trimmedSpec);
    }

    String serverName = trimmedSpec.substring(0, lastSlashIndex).trim();
    String toolName = trimmedSpec.substring(lastSlashIndex + 1).trim();
    if (StringUtils.isBlank(serverName) || StringUtils.isBlank(toolName)) {
      return null;
    }
    return new ToolReference(serverName, toolName);
  }

  private void setToolStatus(Map<String, Map<String, Boolean>> status, String serverName, String toolName,
      boolean enabled) {
    if (StringUtils.isBlank(serverName) || StringUtils.isBlank(toolName)) {
      return;
    }
    status.computeIfAbsent(serverName, key -> new LinkedHashMap<>()).put(toolName, enabled);
  }

  private Map<String, Map<String, Map<String, Boolean>>> deepCopyModeStatus(
      Map<String, Map<String, Map<String, Boolean>>> modeStatus) {
    Map<String, Map<String, Map<String, Boolean>>> copy = new LinkedHashMap<>();
    if (modeStatus == null) {
      return copy;
    }
    for (Map.Entry<String, Map<String, Map<String, Boolean>>> modeEntry : modeStatus.entrySet()) {
      copy.put(modeEntry.getKey(), deepCopyServerStatus(modeEntry.getValue()));
    }
    return copy;
  }

  private Map<String, Map<String, Boolean>> deepCopyServerStatus(Map<String, Map<String, Boolean>> serverStatus) {
    Map<String, Map<String, Boolean>> copy = new LinkedHashMap<>();
    if (serverStatus == null) {
      return copy;
    }
    for (Map.Entry<String, Map<String, Boolean>> serverEntry : serverStatus.entrySet()) {
      copy.put(serverEntry.getKey(), new LinkedHashMap<>(serverEntry.getValue()));
    }
    return copy;
  }

  private static class ToolReference {
    private final String serverName;
    private final String toolName;

    private ToolReference(String serverName, String toolName) {
      this.serverName = serverName;
      this.toolName = toolName;
    }
  }
}
