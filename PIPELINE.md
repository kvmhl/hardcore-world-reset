# HardcoreWorldReset CI/CD Pipeline

This document explains how to set up and use the GitHub Actions pipeline.

---

## Prerequisites

Before pushing to GitHub, make sure you have:

1. **A GitHub account** (free tier works fine)
2. **Git installed** and configured with your GitHub credentials
3. **A new GitHub repository** (public or private)

---

## Initial Setup (One Time)

### Step 1: Create a GitHub Repository

1. Go to [github.com/new](https://github.com/new)
2. Enter repository name: `hardcore-world-reset` (or whatever you prefer)
3. Set to **Public** or **Private**
4. **DO NOT** initialize with README (you already have one)
5. Click **Create repository**

### Step 2: Connect Your Local Repo

After creating the repo, GitHub shows commands. Run these in your project folder:

```powershell
# Add GitHub as remote (replace YOUR_USERNAME with your GitHub username)
git remote add origin https://github.com/YOUR_USERNAME/hardcore-world-reset.git

# Push to GitHub
git branch -M main
git push -u origin main
```

### Step 3: Verify Pipeline Works

1. Go to your repository on GitHub
2. Click the **Actions** tab
3. You should see the "Build and Test" workflow running
4. Wait for it to complete (green checkmark = success)

---

## Creating a Release

When you're ready to publish a new version:

```powershell
# Make sure all changes are committed
git add -A
git commit -m "Ready for release v2.0.0"

# Create a version tag
git tag v2.0.0

# Push the tag to GitHub
git push origin v2.0.0
```

The pipeline will automatically:
1. Build the plugin
2. Run all tests
3. Create a GitHub Release page
4. Attach the compiled JAR file

---

## Pipeline Triggers

| When | What Happens |
|------|--------------|
| Push to `main` or `develop` | Build + Test |
| Open a Pull Request | Build + Test |
| Push a `v*` tag (e.g., `v2.0.0`) | Build + Test + Create Release |

---

## Downloading Artifacts

Even without creating a release, you can download the built JAR:

1. Go to **Actions** tab
2. Click on a completed workflow run
3. Scroll to **Artifacts**
4. Download **HardcoreWorldReset-Plugins**

---

## Troubleshooting

### Build Fails

- Check the Actions tab for error logs
- Make sure `pom.xml` is valid
- Ensure all Java files compile locally with `mvn clean package` (or `.\mvnw.cmd clean package`)

### Tests Fail

- Run tests locally first: `mvn test`
- Check test output in the Actions log

### Release Not Created

- Make sure the tag starts with `v` (e.g., `v2.0.0`, not `2.0.0`)
- Verify the tag was pushed: `git push origin --tags`

---

## Permissions Note

The pipeline uses the default `GITHUB_TOKEN` which is automatically provided by GitHub Actions. No additional secrets or configuration needed!
