# 🛠️ DreamBot AI Quest System - Coding Rules & Guidelines

**MANDATORY REFERENCE FILE** - These rules must be followed for all code modifications and development.

---

## 📋 File Management Rules

### Clean Up Compiled Files
- **Always clean up .class files** that get generated during compilations
- Add `.class` files to `.gitignore` to prevent them from being committed
- Use build tools or scripts to automatically clean compiled files
- Before committing code, ensure no compiled artifacts are included

### Recommended .gitignore entries:
```
*.class
*.jar
build/
target/
out/
```

## 🎨 Code Style Guidelines

### Emoji Usage
- **NEVER use emojis in actual coded logic** for game tasks, handlers, or any functional code
- Emojis are acceptable only in:
  - Comments for documentation purposes
  - Log messages for debugging (use sparingly)
  - README files or documentation
  - Test files for clarity

### Examples:
```java
// ❌ BAD - Don't do this
if (player.getHealth() < 50) {
    log("Health low! 😰");
    eatFood("Lobster 🦞");
}

// ✅ GOOD - Clean, professional code
if (player.getHealth() < 50) {
    log("Health low - consuming food");
    eatFood("Lobster");
}
```

## 📚 DreamBot API Usage

### Quest Implementation Guidelines
- **ALWAYS verify quest steps** against official OSRS Wiki guides: https://oldschool.runescape.wiki/w/[Quest_Name]
- **Check step order** - ensure quest steps are implemented in the correct sequence
- **Verify dialogue options** - match exact dialogue text from wiki guides
- **Validate locations** - confirm correct coordinates and areas for each quest step
- **Cross-reference requirements** - ensure all quest requirements and items are handled properly

### Consumed Item Resumability (Critical Rule)
- **NEVER rely solely on consumable items for state detection** - Items like keys, potions, food dissolve/disappear after use
- **Use multiple detection methods for progress tracking:**
  - **Position/Area detection** - Check if player is in areas that indicate progress
  - **Game state detection** - Use configs, varbits, or quest progress values
  - **Environmental markers** - Check if doors are opened, NPCs are killed, objects are interacted with
  - **Z-level detection** - Use floor/height changes to detect progression
- **Always implement fallback logic** for when consumable items are no longer present
- **Test resumability scenarios** - Test bot behavior when restarting after each major step
- **Example Issue**: Bot has red key → opens door → key dissolves → bot restarts because it can't find key
- **Example Fix**: Detect if player is past the door area, on higher floor, or in post-door locations

### API Documentation Reference

### API Documentation
- **NEVER guess the DreamBot API methods or classes**
- Always reference the official DreamBot JavaDocs: https://dreambot.org/javadocs/index-all.html
- When unsure about a method, search the documentation first
- Use the exact method signatures and parameters as documented

### API Research Process:
1. Check the official JavaDocs at https://dreambot.org/javadocs/index-all.html
2. Search for the specific class or method you need
3. Review the method parameters and return types
4. Look for usage examples in the documentation
5. Test the method in a controlled environment before full implementation

### Common API Categories to Reference:
- **Methods**: `org.dreambot.api.methods.*`
- **Wrappers**: `org.dreambot.api.wrappers.*`
- **Utilities**: `org.dreambot.api.utilities.*`
- **Script**: `org.dreambot.api.script.*`

## 🚀 Development Best Practices

### Code Organization
- Keep handlers in the `handlers/` directory
- Use clear, descriptive class and method names
- Follow Java naming conventions
- Add comprehensive logging for debugging

### Error Handling
- Always include try-catch blocks for critical operations
- Log errors with context information
- Provide fallback behaviors when possible
- Don't silence exceptions without proper logging

### Performance Considerations
- Use Sleep.sleepUntil() for condition-based waiting
- Avoid excessive polling in tight loops
- Cache frequently accessed game objects when appropriate
- Use random delays to simulate human behavior

### Documentation
- Comment complex logic thoroughly
- Document API usage patterns
- Include examples for custom methods
- Keep comments up-to-date with code changes

## 🧪 Testing Guidelines

### Before Deployment
- Test all handlers individually
- Verify API method calls work as expected
- Check for memory leaks in long-running scripts
- Ensure proper cleanup in onExit() methods

### Debugging
- Use comprehensive logging levels
- Include state information in log messages
- Test edge cases and error conditions
- Validate game state before actions

## 🔧 Repository Maintenance

### Commit Practices
- Clean up generated files before committing
- Use descriptive commit messages
- Test code before pushing
- Review changes for sensitive information

### Branch Management
- Use feature branches for new development
- Keep main branch stable
- Merge only tested and reviewed code
- Document breaking changes

---

**Remember**: Professional, clean code is maintainable code. Always prioritize clarity and correctness over shortcuts.