import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const spec = JSON.parse(await readFile(new URL('./openapi.json', import.meta.url), 'utf8'))
const methods = new Set(['get', 'post', 'put', 'patch', 'delete'])
const operations = Object.entries(spec.paths || {}).flatMap(([path, item]) =>
  Object.entries(item)
    .filter(([method]) => methods.has(method))
    .map(([method, operation]) => ({ path, method, operation })))

test('OpenAPI exposes one versioned business surface with unique operation IDs', () => {
  const ids = operations.map(({ operation }) => operation.operationId)
  assert.equal(ids.every(Boolean), true)
  assert.equal(new Set(ids).size, ids.length)
  assert.equal(operations.some(({ path }) => /^\/api\/(auth|worktime|ledger)(?:\/|$)/.test(path)), false)
  assert.equal(operations.some(({ path }) => path === '/api/data' || path.includes('/snapshot')), false)
})

test('OpenAPI schemas are constrained and hide persistence-only identifiers', () => {
  const schemas = Object.entries(spec.components?.schemas || {})
  assert.equal(schemas.some(([, schema]) => schema.type === 'object' && schema.additionalProperties === true), false)
  assert.equal(schemas.some(([, schema]) => Object.hasOwn(schema.properties || {}, 'internalId')), false)
  assert.ok(spec.components.schemas.SyncEntity.oneOf)
  assert.ok(spec.components.schemas.RestoreView.oneOf)
})

test('every operation declares the typed RFC 7807 error contract', () => {
  const problem = spec.components?.schemas?.ApiProblem
  const properties = new Set(Object.keys(problem?.properties || {}))
  for (const property of ['type', 'title', 'status', 'detail', 'instance', 'code', 'traceId', 'path', 'timestamp', 'serverRevision']) {
    assert.equal(properties.has(property), true, `ApiProblem is missing ${property}`)
  }
  for (const { path, method, operation } of operations) {
    for (const status of ['400', '401', '403', '409', '410', '500']) {
      const schema = operation.responses?.[status]?.content?.['application/problem+json']?.schema
      assert.equal(schema?.$ref, '#/components/schemas/ApiProblem', `${method.toUpperCase()} ${path} is missing ${status}`)
    }
  }
})
