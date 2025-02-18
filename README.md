# 🧠 RuneGPT

<div style="text-align:center">
    
![RuneGPT](https://raw.githubusercontent.com/Kamran14/RuneGPT/refs/heads/master/src/main/resources/com/kamrant/runegpt/logo.png)

</div>

Ask Gemini about Old School RuneScape (OSRS) using your current player stats and bank items!

## 🚀 Features
- 🖥️ Easy-to-Use Panel: Ask questions directly from RuneLite’s sidebar.
- ⚙️ Configurable: Securely enter and store your Gemini API key via RuneLite’s plugin settings.

### UPCOMING:
- 🛡️ Context-Aware: Queries include your stats and optional bank items for tailored suggestions.
- 🔹 Markdown-Style Display: View responses in a clean, markdown-styled panel with headings, lists, and bold highlights.

### Showcase
Asking a question about best bloodveld location when using cannons
![Cannon Question](https://i.imgur.com/kaggxs8.png)

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

## ✨ Usage

- Open the RuneGPT Panel from the sidebar.
- Enter a question like:
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
