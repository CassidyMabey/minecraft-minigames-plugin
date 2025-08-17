
# Minecraft Minigames Plugin

Welcome to the **Minecraft Minigames Plugin**! This plugin brings a collection of fun, competitive, and engaging minigames to your Minecraft server. Designed and developed as a student project, it aims to provide a seamless and enjoyable experience for players of all skill levels.

## Table of Contents
- [Overview](#overview)
- [Minigames Included](#minigames-included)
	- [Find the Button](#find-the-button)
	- [Find the Item](#find-the-item)
	- [Hot Potato](#hot-potato)
	- [Parkour](#parkour)
	- [Spleef](#spleef)
	- [Super Smash Steves](#super-smash-steves)
- [Voting System](#voting-system)
- [Player Stats & Winners](#player-stats--winners)
- [Installation](#installation)
- [Usage](#usage)
- [License](#license)

---

## Overview
This plugin adds a queue system and a variety of minigames to your Minecraft server. Players can join the queue, vote for their favorite game, and compete to win! The plugin tracks stats and winners, making every game session exciting and rewarding.

## How It Works

### Starting Games
1. Players use `/game start` to begin the queue system
2. **EVERYONE** on the server gets teleported to the selected minigame arena when it starts
3. A voting period begins (2 minutes) where players can vote for their preferred minigame
4. The game with the most votes is selected and **ALL PLAYERS** (except those in Creative mode) are automatically teleported and included in the game

### Voting System
- **Smart Voting**: The previously played game is automatically excluded from the next voting round to ensure variety
- **No Repeat Votes**: Players cannot vote for the same game multiple times in one voting session
- **Real-time Updates**: Vote counts are displayed in the chat every 15 seconds and on the scoreboard
- **Visual Feedback**: Progress bars show vote percentages for each game mode
- **Universal Participation**: Whether you voted or not, **EVERYONE gets teleported and included when the game starts**

### Winner Recognition System
After each game, the plugin features a sophisticated winner display system:
- **Podium Display**: Top 3 players are displayed on armor stands at designated podium locations
- **Player Heads**: Each armor stand shows the actual player's head/skin for personalization
- **Colored Outfits**: 1st place gets gold armor, 2nd gets silver, 3rd gets bronze
- **Live Leaderboard**: The podium updates automatically every 10 seconds with current rankings
- **Point Tracking**: Winners earn points that accumulate over time for persistent rankings

## Minigames Included

### Find the Button
Players are placed in a custom map and must race to find a hidden button. The first player to locate and press the button wins the round. This game tests players' observation skills and speed.

### Find the Item
Each player is given a list of items to find within a set time limit. Items are hidden in chests around the map. The player who finds all items first, or the most items before time runs out, is the winner. This game encourages exploration and quick thinking.

### Hot Potato
Players pass around a "hot potato" (an item) by right-clicking other players. If you're holding the potato when the timer runs out, you're eliminated! The last player remaining wins. This game is fast-paced and full of suspense.

### Parkour
Players must complete a challenging parkour course as quickly as possible. The first to reach the end is the winner. This game is perfect for players who love testing their agility and jumping skills.

### Spleef
Players compete in an arena where the floor can be destroyed. The goal is to make your opponents fall into the void by breaking blocks beneath them. The last player standing wins. Spleef is a classic Minecraft minigame that requires strategy and quick reflexes.

**Technical Implementation**: The plugin automatically regenerates snow blocks at the start of each Spleef game. It creates three 51x51 snow platforms at different heights using a simple loop system that places `Material.SNOW_BLOCK` in a grid pattern around predefined center points. This ensures a fresh arena for every round!

### Super Smash Steves
Inspired by popular fighting games, players battle in an arena with knockback weapons and special abilities. The objective is to knock other players out of the arena. The last player remaining is crowned the winner. This game is chaotic, fun, and action-packed.

### TNT Run
Players must keep moving as TNT blocks disappear beneath their feet! The ground explodes 0.5 seconds after being touched, so standing still means certain doom. The last player remaining wins.

**Technical Implementation**: Similar to Spleef, the plugin regenerates the TNT arena before each game. It builds three 51x51 platforms using `Material.TNT` blocks, with a bedrock center block for reference. The regeneration system ensures players always have a full arena to compete in, regardless of how the previous game ended.

## Advanced Features

### Arena Regeneration System
I implemented a smart arena regeneration system for games that modify the environment:
- **Spleef**: Completely rebuilds all snow platforms before each game starts
- **TNT Run**: Regenerates all TNT blocks in the three-layer arena
- **Automated Cleanup**: No manual intervention needed - the plugin handles everything automatically
- **Consistent Experience**: Every game starts with a pristine, full arena

This was achieved by storing the coordinates of arena center points and using nested loops to place blocks in a grid pattern around these centers. The system runs synchronously during game initialization to ensure the arena is ready before players are teleported.

## Installation
1. Download the latest plugin JAR from the [releases](#) (or build from source).
2. Place the JAR file in your server's `plugins` folder.
3. Restart your server.
4. Configure settings in `plugin.yml` or in-game as needed.

## Usage
### Starting the Game
- Use `/game start` to begin a game session
- **ALL PLAYERS** on the server are automatically included (except Creative mode players)
- **EVERYONE gets teleported** to the game arena regardless of voting participation
- Wait for the 2-minute voting period to begin

### During Voting
- Click on the game mode items in your inventory to vote
- Watch real-time vote counts in chat and on the scoreboard
- The game with the most votes wins (previous game is excluded)
- Voting ends automatically after 2 minutes
- **Important**: You don't need to vote to participate - ALL players are teleported when the game starts!

### Game Commands
- `/game start` - Start a new game session (teleports EVERYONE)
- `/queue stats` - Check your personal statistics and ranking
- `/queue join` - Manually join the queue (if available)

### Spectating and Participation
- Players in Creative mode are automatically excluded from games
- All other players are forcibly teleported and included in every game
- Eliminated players can spectate ongoing matches
- Winners are automatically added to the leaderboard and podium display

## License
This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---
