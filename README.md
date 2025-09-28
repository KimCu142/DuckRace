# Duck Race Android App

A fun duck racing game built for Android using Java and XML layouts.

## Features

- **Configurable Race**: Choose between 3-8 ducks for each race
- **Realistic Physics**: Each duck has different base speeds and random boost mechanics
- **Smooth Animation**: 60fps game loop with smooth duck movement
- **Countdown Timer**: 3-2-1-GO countdown before race starts
- **Winner Detection**: Automatic winner announcement when first duck crosses finish line
- **Reset Functionality**: Reset race to start over

## How to Play

1. Select the number of ducks (3-8) using the spinner
2. Tap "Start" to begin the countdown
3. Watch the ducks race to the finish line
4. The first duck to cross the red finish line wins!
5. Use "Reset" to start a new race

## Technical Details

- **Language**: Java
- **UI**: XML layouts with ConstraintLayout
- **Animation**: Handler-based game loop at 60fps
- **Physics**: Custom speed and acceleration system with random boosts
- **Ducks**: Emoji-based (🦆) - no image assets required

## Project Structure

- `activity_main.xml` - Main game interface with controls and race track
- `item_duck_lane.xml` - Individual duck lane layout
- `MainActivity.java` - Game logic, physics, and race management

## Build Instructions

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on device or emulator

The app is ready to run and requires no additional setup or dependencies beyond the standard Android SDK.
