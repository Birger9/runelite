/*
 * Copyright (c) 2022, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.timetracking.farming;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.devtools.ScriptInspector;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.MenuAction;

@Slf4j
@RequiredArgsConstructor(
	access = AccessLevel.PRIVATE,
	onConstructor = @__({@Inject})
)
@Singleton
public class PaymentTracker
{
	private static final String PAYMENT_MALE = "That'll do nicely, sir. Leave it with me - I'll make sure<br>that patch grows for you.";
	private static final String PAYMENT_FEMALE = "That'll do nicely, madam. Leave it with me - I'll make<br>sure that patch grows for you.";

	private final Client client;
	private final ConfigManager configManager;
	private final FarmingWorld farmingWorld;
	private int chosenPatch;

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		log.debug("CHOSEN PATCH IS {}", chosenPatch);
		Widget text = client.getWidget(ComponentID.DIALOG_NPC_TEXT);
		if (text == null || (!PAYMENT_MALE.equals(text.getText()) && !PAYMENT_FEMALE.equals(text.getText())))
		{
			return;
		}

		Widget name = client.getWidget(ComponentID.DIALOG_NPC_NAME);
		Widget head = client.getWidget(ComponentID.DIALOG_NPC_HEAD_MODEL);
		if (name == null || head == null || head.getModelType() != WidgetModelType.NPC_CHATHEAD)
		{
			return;
		}

		log.debug("CHOSEN PATCH IS BEFORE PATCH {}", chosenPatch);
		final int npcId = head.getModelId();
		final FarmingPatch patch = findPatchForNpc(npcId);

		if (patch == null)
		{
			return;
		}

		if (getProtectedState(patch))
		{
			return;
		}

		log.debug("Detected patch payment for {} ({})", name.getText(), npcId);
		setProtectedState(patch, true);
	}

	private static String configKey(FarmingPatch fp)
	{
		return fp.configKey() + "." + TimeTrackingConfig.PROTECTED;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked) {
		switch (menuOptionClicked.getMenuAction()) {
			case NPC_THIRD_OPTION:
				chosenPatch = 1;
				break;
			case NPC_FOURTH_OPTION:
				chosenPatch = 2;
				break;
			case WIDGET_CONTINUE:
				chosenPatch = menuOptionClicked.getActionParam();
				break;
			default:
				chosenPatch = -1;
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != 57)
		{
			return;
		}

		final String[] stringStack = client.getStringStack();
		if(!stringStack[0].isEmpty()) {
			log.debug("CHOSEN KEY IS {}", stringStack[0]);
			chosenPatch = (Integer.parseInt(stringStack[0]) == 0) ? 2 : Integer.parseInt(stringStack[0]);
		}
	}

	public void setProtectedState(FarmingPatch fp, boolean state)
	{
		if (!state)
		{
			configManager.unsetRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, configKey(fp));
		}
		else
		{
			configManager.setRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, configKey(fp), state);
		}
	}

	public boolean getProtectedState(FarmingPatch fp)
	{
		return Boolean.TRUE.equals(configManager.getRSProfileConfiguration(
			TimeTrackingConfig.CONFIG_GROUP,
			configKey(fp),
			Boolean.class));
	}

	private FarmingPatch findPatchForNpc(int npcId)
	{
		FarmingPatch p = null;
		for (FarmingRegion region : farmingWorld.getRegionsForLocation(client.getLocalPlayer().getWorldLocation()))
		{
			for (FarmingPatch patch : region.getPatches())
			{
				if (patch.getFarmer() == npcId)
				{
					if (chosenPatch == 1 && Objects.equals(patch.getName(), "North"))
					{
						p = patch;
					}
					else if (chosenPatch == 2 && Objects.equals(patch.getName(), "South"))
					{
						p = patch;
					}
				}
			}
		}

		chosenPatch = -1;
		return p;
	}
}
