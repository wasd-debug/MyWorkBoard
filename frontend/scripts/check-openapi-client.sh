#!/usr/bin/env bash
set -euo pipefail

frontend_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
generated_dir="$frontend_dir/packages/api-client/src/generated"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

cd "$frontend_dir"
npx openapi-generator-cli generate \
  -i packages/api-client/openapi.json \
  -g typescript-axios \
  -o "$temporary_dir/generated" \
  --ignore-file-override packages/api-client/src/generated/.openapi-generator-ignore \
  --additional-properties=supportsES6=true,withSeparateModelsAndApi=true,apiPackage=api,modelPackage=models,useSingleRequestParameter=true,stringEnums=true,enumPropertyNaming=original,importFileExtension=.ts \
  --global-property=apiDocs=false,modelDocs=false,apiTests=false,modelTests=false \
  >/dev/null

rm -f "$temporary_dir/generated/git_push.sh" "$temporary_dir/generated/.gitignore" "$temporary_dir/generated/.npmignore"
rm -rf "$temporary_dir/generated/.openapi-generator"
cp packages/api-client/src/generated/.openapi-generator-ignore "$temporary_dir/generated/.openapi-generator-ignore"
node scripts/normalize-generated-client.mjs "$temporary_dir/generated"
diff -ru "$generated_dir" "$temporary_dir/generated"
npm run typecheck
