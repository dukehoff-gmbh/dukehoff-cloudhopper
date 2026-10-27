#!/bin/bash

set -e  # sofort abbrechen bei Fehlern

# Farben für schönere Ausgabe
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting Cloudhopper Release...${NC}"

# 1. Prüfen, ob Maven installiert ist
if ! command -v mvn &> /dev/null; then
    echo "Maven could not be found. Please install Maven first."
    exit 1
fi

# 2. Aktuelle Version auslesen
current_version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
release_version=${current_version/-SNAPSHOT/}
next_snapshot_version=$(echo $release_version | awk -F. -v OFS=. '{$NF += 1; print $0 "-SNAPSHOT"}')

echo -e "${GREEN}Current version:${NC} $current_version"
echo -e "${GREEN}Release version:${NC} $release_version"
echo -e "${GREEN}Next snapshot version:${NC} $next_snapshot_version"

# 3. Bestätigung holen
read -p "Do you want to release version ${release_version}? (y/n) " -n 1 -r
echo    # new line
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

# 4. Version auf Release-Version setzen
echo -e "${GREEN}Setting release version...${NC}"
mvn versions:set -DnewVersion=${release_version}
mvn versions:commit

# 5. Commit und Tag
git commit -am "Release version ${release_version}"
git tag -a "v${release_version}" -m "Release version ${release_version}"

# 6. Release bauen und deployen
echo -e "${GREEN}Deploying release to OSSRH...${NC}"
mvn clean deploy -P release

# 7. Version auf nächste Snapshot-Version erhöhen
echo -e "${GREEN}Preparing next development version...${NC}"
mvn versions:set -DnewVersion=${next_snapshot_version}
mvn versions:commit

git commit -am "Prepare next development iteration: ${next_snapshot_version}"

# 8. Pushen
git push origin main
git push origin --tags

# 9. Release-Notes automatisch erstellen
echo -e "${GREEN}Generating release notes...${NC}"

# Finde den letzten Git-Tag (der vorherige Release)
last_tag=$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || echo "")

if [[ -z "$last_tag" ]]; then
    echo "No previous tag found, using initial commit."
    changelog=$(git log --pretty=format:"* %s" HEAD)
else
    echo "Previous tag: $last_tag"
    changelog=$(git log "$last_tag"..HEAD --pretty=format:"* %s")
fi

release_notes_file="release-notes/v${release_version}.md"

mkdir -p release-notes
echo "# Release v${release_version}" > "$release_notes_file"
echo "" >> "$release_notes_file"
echo "## Changes" >> "$release_notes_file"
echo "" >> "$release_notes_file"
echo "$changelog" >> "$release_notes_file"

echo -e "${GREEN}Release notes generated at:${NC} ${release_notes_file}"

echo -e "${GREEN}Release ${release_version} successfully deployed, pushed and documented!${NC}"
