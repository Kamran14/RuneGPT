# 🧠 RuneGPT

<div style="text-align:center">
    
![RuneGPT](https://raw.githubusercontent.com/Kamran14/RuneGPT/refs/heads/master/src/main/resources/com/kamrant/runegpt/logo.png)

</div>

Ask Gemini, Claude or Groq about Old School RuneScape (OSRS) using your current player stats and bank items!

## 🚀 Features
- 🖥️ Easy-to-Use Panel: Ask questions directly from RuneLite’s sidebar.
- ⚙️ Configurable: Securely enter and store your Gemini API key via RuneLite’s plugin settings.
- 📊 Stats: User stats are automatically accounted for to cater for every unique request.

### UPCOMING:
- 🛡️ Context-Aware: Queries include your bank items for tailored suggestions.
- ⚔️🤺 Combat Items: Bring the best items you got for the fight! 
- 🔹 Markdown-Style Display: View responses in a clean, markdown-styled panel with headings, lists, and bold highlights.
- 🔌 Wiki Integration: Suggestions will reflect up-to-date guides
- 🔌 GE Integration: Some responses will have real-time prices (Will require osrs API key)
### Showcase
Questions about stats and quests integrated into plugin.
![Time constraint](https://raw.githubusercontent.com/Kamran14/RuneGPT/refs/heads/master/imgs/stats_response2.png)
![Slayer Question](https://github.com/Kamran14/RuneGPT/blob/master/imgs/stats_response.png?raw=true)

## 🛠️ Setup
1. Install the Plugin:
    - Add the plugin via the RuneLite Plugin Hub.
    - Enable RuneGPT from the RuneLite settings.

2. Enter Your API Key (BYOK – Bring Your Own Key):

You must bring your own API key for Gemini (Google). This plugin does not provide one.

Why?

- 💳 API keys are tied to your Google account. (OpenAI upcoming)
- 🚫 This plugin does not store or share your key. It stays local on your machine.

🔑 How to Add Your API Key:
- Go to Google [AI Studio](https://aistudio.google.com/app/apikey), click `Get API Key` and copy the key you just created
- In RuneLite, go to Configuration (🛠️ wrench icon).
- Find RuneGPT Plugin under settings.
- Enter your Google Gemini API Key.

🤖 Other providers (add as many keys as you like):
- **Claude**: create a key at the [Anthropic Console](https://console.anthropic.com/settings/keys) and paste it into **Claude API Key**. Optionally change **Claude Model** (default `claude-opus-5-5`; `claude-sonnet-5-5` or `claude-haiku-5-5` are cheaper).
- **Groq (free, no card)**: create a key at the [Groq Console](https://console.groq.com/keys) and paste it into **Groq API Key**. The default model is `openai/gpt-oss-120b`.

🔁 Automatic failover:
- **Primary LLM Provider** is tried first. If it errors or runs out of tokens, RuneGPT moves on to the next provider that has a key, without you doing anything.
- Your conversation is kept inside the plugin, so whichever provider answers can see the earlier messages. Use **Clear** in the panel to start a new conversation.
- You only see an error once every configured provider has failed.

![API config](https://raw.githubusercontent.com/Kamran14/RuneGPT/refs/heads/master/imgs/config_panel.png)
## ✨ Usage

- Open the RuneGPT Panel from the sidebar.
- **Guides tab**: pick a category (Quests, Monsters, Bosses, Skilling, Money, Minigames) to see suggestions based on your stats and quest progress, e.g. "Continue *quest*" for quests you've started, or "Train *your lowest skill* to *next milestone*".
    - **Ask** sends the guide to the chat.
    - **Edit** lets you change its details (e.g. the monster or kill count) or rewrite the whole question. Your edits are saved; **Reset to default** undoes them.
    - **Other** at the bottom of each category lets you type your own interest.
- **Chat tab**: answers are formatted (headings, lists, bold, links to the wiki). Ask follow-ups, or press **New chat** to start over.
- Or just type a question like:
    - "What’s the best setup for Barrows with my stats?"
    - "Whats the best way to level my agility?"
    - "Whats a good money making method basded on my stats?"
    - "Can I solo Zulrah with my current gear?" (upcoming)

## 🛡️ Security

- Your API key is stored locally via RuneLite’s secure configuration.
- No keys or data are sent anywhere except directly to the LLM provider.

## 💸 Costs

This plugin uses Google Gemini API, which currently offers free API access with generous usage limits. Check Google's terms for any updates on usage policies.
### Note: OpenAI and others require you to add a payment method whereas Google does not. This was the main factor in picking the service.






#### No, RuneGPT isn’t a true GPT model since it isn’t pre-trained. The name was chosen more for fun, though the project could eventually head in that direction.
