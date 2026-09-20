// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.preferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.copilot.eclipse.core.chat.CustomChatMode;

/**
 * Synchronizes custom-agent preferences with explicit tool lists from agent definitions.
 */
final class CustomAgentToolStatusResolver {

  private CustomAgentToolStatusResolver() {
  }

  static boolean synchronizeModePreferences(
      Map<String, Map<String, Map<String, Boolean>>> modeToolStatus,
      Iterable<CustomChatMode> customModes, String builtInServerName,
      Map<String, Map<String, Boolean>> availableToolStatus) {
    boolean changed = false;
    for (CustomChatMode mode : customModes) {
      Map<String, Map<String, Boolean>> resolvedStatus;
      if (mode.hasExplicitToolList()) {
        resolvedStatus = resolve(mode, builtInServerName, availableToolStatus);
      } else if (!modeToolStatus.containsKey(mode.getId()) && availableToolStatus != null) {
        resolvedStatus = copyAvailableToolStatus(availableToolStatus, true);
      } else {
        continue;
      }

      if (!Objects.equals(modeToolStatus.get(mode.getId()), resolvedStatus)) {
        modeToolStatus.put(mode.getId(), resolvedStatus);
        changed = true;
      }
    }
    return changed;
  }

  static Map<String, Map<String, Boolean>> resolve(CustomChatMode mode, String builtInServerName) {
    return resolve(mode, builtInServerName, null);
  }

  static Map<String, Map<String, Boolean>> resolve(CustomChatMode mode, String builtInServerName,
      Map<String, Map<String, Boolean>> availableToolStatus) {
    Map<String, Map<String, Boolean>> resolvedStatus = copyAvailableToolStatus(availableToolStatus, false);
    for (String toolSpecification : mode.getTools()) {
      if (StringUtils.isBlank(toolSpecification)) {
        continue;
      }

      int separatorIndex = toolSpecification.lastIndexOf('/');
      String serverName = separatorIndex < 0
          ? builtInServerName
          : toolSpecification.substring(0, separatorIndex);
      String toolName = separatorIndex < 0
          ? toolSpecification
          : toolSpecification.substring(separatorIndex + 1);
      if (StringUtils.isBlank(serverName) || StringUtils.isBlank(toolName)) {
        continue;
      }

      resolvedStatus.computeIfAbsent(serverName, key -> new HashMap<>()).put(toolName, true);
    }
    return resolvedStatus;
  }

  private static Map<String, Map<String, Boolean>> copyAvailableToolStatus(
      Map<String, Map<String, Boolean>> availableToolStatus, boolean enabled) {
    Map<String, Map<String, Boolean>> copiedStatus = new HashMap<>();
    if (availableToolStatus == null) {
      return copiedStatus;
    }

    for (Map.Entry<String, Map<String, Boolean>> serverEntry : availableToolStatus.entrySet()) {
      if (StringUtils.isBlank(serverEntry.getKey()) || serverEntry.getValue() == null) {
        continue;
      }
      Map<String, Boolean> toolStatus = new HashMap<>();
      for (String toolName : serverEntry.getValue().keySet()) {
        if (StringUtils.isNotBlank(toolName)) {
          toolStatus.put(toolName, enabled);
        }
      }
      if (!toolStatus.isEmpty()) {
        copiedStatus.put(serverEntry.getKey(), toolStatus);
      }
    }
    return copiedStatus;
  }
}
