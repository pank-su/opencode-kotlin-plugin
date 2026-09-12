import fs from "node:fs"
import path from "node:path"
import ts from "typescript"
import { fileURLToPath } from "node:url"

const here = path.dirname(fileURLToPath(import.meta.url))
const spikeRoot = path.resolve(here, "..")
const packageRoot = path.join(spikeRoot, "node_modules", "@opencode-ai", "plugin")
const outputRoot = path.join(packageRoot, ".karakum-sanitized")

function declarations(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const absolute = path.join(directory, entry.name)
    if (entry.isDirectory()) return declarations(absolute)
    return entry.name.endsWith(".d.ts") ? [absolute] : []
  })
}

const distRoot = path.join(packageRoot, "dist")
const toPosix = value => value.split(path.sep).join("/")
const inputFiles = declarations(distRoot).sort((left, right) =>
  toPosix(path.relative(distRoot, left)).localeCompare(toPosix(path.relative(distRoot, right))),
)
const program = ts.createProgram(inputFiles, {
  module: ts.ModuleKind.ESNext,
  moduleResolution: ts.ModuleResolutionKind.Bundler,
  skipLibCheck: true,
  target: ts.ScriptTarget.ESNext,
})
const checker = program.getTypeChecker()
const factory = ts.factory
const printer = ts.createPrinter({ newLine: ts.NewLineKind.LineFeed })

const pluginDeclaredNames = new Set()
const typeParameterNames = new Set()
for (const sourceFile of program.getSourceFiles()) {
  if (!inputFiles.includes(sourceFile.fileName)) continue
  for (const statement of sourceFile.statements) {
    if (statement.name && ts.isIdentifier(statement.name)) pluginDeclaredNames.add(statement.name.text)
  }
  const collectTypeParameters = (node) => {
    if (ts.isTypeParameterDeclaration(node)) typeParameterNames.add(node.name.text)
    ts.forEachChild(node, collectTypeParameters)
  }
  collectTypeParameters(sourceFile)
}

fs.rmSync(outputRoot, { recursive: true, force: true })

const utilityTypes = new Set([
  "Awaited",
  "ConstructorParameters",
  "Exclude",
  "Extract",
  "InstanceType",
  "Lowercase",
  "NonNullable",
  "Omit",
  "Parameters",
  "Partial",
  "Pick",
  "Readonly",
  "Required",
  "ReturnType",
  "ThisParameterType",
  "Uppercase",
])
const builtinTypes = new Set([
  "Array",
  "Map",
  "Promise",
  "ReadonlyArray",
  "ReadonlyMap",
  "ReadonlySet",
  "Record",
  "Set",
])

function leftmostName(name) {
  while (ts.isQualifiedName(name)) name = name.left
  return ts.isIdentifier(name) ? name.text : ""
}

function memberNameText(member) {
  const name = member.name
  if (!name) return undefined
  if (ts.isIdentifier(name) || ts.isStringLiteral(name) || ts.isNumericLiteral(name)) return name.text
  return undefined
}

for (const fileName of inputFiles) {
  const sourceFile = program.getSourceFile(fileName)
  if (!sourceFile) throw new Error(`TypeScript did not load ${fileName}`)

  const externalImports = new Set()
  for (const statement of sourceFile.statements) {
    if (!ts.isImportDeclaration(statement) || !ts.isStringLiteral(statement.moduleSpecifier)) continue
    if (statement.moduleSpecifier.text.startsWith(".")) continue
    const clause = statement.importClause
    if (clause?.name) externalImports.add(clause.name.text)
    const bindings = clause?.namedBindings
    if (bindings && ts.isNamespaceImport(bindings)) externalImports.add(bindings.name.text)
    if (bindings && ts.isNamedImports(bindings)) {
      for (const element of bindings.elements) externalImports.add(element.name.text)
    }
  }

  let transformationContext

  const unknownType = () => factory.createKeywordTypeNode(ts.SyntaxKind.UnknownKeyword)
  const isExternalReference = (typeName) => externalImports.has(leftmostName(typeName))

  function safeType(node) {
    if (!node) return unknownType()

    if (ts.isLiteralTypeNode(node)) {
      if (ts.isStringLiteral(node.literal)) return factory.createKeywordTypeNode(ts.SyntaxKind.StringKeyword)
      if (ts.isNumericLiteral(node.literal)) return factory.createKeywordTypeNode(ts.SyntaxKind.NumberKeyword)
      if (node.literal.kind === ts.SyntaxKind.TrueKeyword || node.literal.kind === ts.SyntaxKind.FalseKeyword) {
        return factory.createKeywordTypeNode(ts.SyntaxKind.BooleanKeyword)
      }
      return unknownType()
    }

    if (node.kind === ts.SyntaxKind.VoidKeyword || node.kind === ts.SyntaxKind.NeverKeyword || node.kind === ts.SyntaxKind.ThisType) {
      return unknownType()
    }
    if (ts.isTypePredicateNode(node)) return factory.createKeywordTypeNode(ts.SyntaxKind.BooleanKeyword)
    if (ts.isImportTypeNode(node) || ts.isTypeQueryNode(node) || ts.isIndexedAccessTypeNode(node)) return unknownType()
    if (ts.isConditionalTypeNode(node) || ts.isMappedTypeNode(node) || ts.isInferTypeNode(node)) return unknownType()

    if (ts.isUnionTypeNode(node)) {
      const hasNullish = node.types.some((type) =>
        type.kind === ts.SyntaxKind.UndefinedKeyword || type.kind === ts.SyntaxKind.NullKeyword,
      )
      const meaningful = node.types.filter((type) =>
        type.kind !== ts.SyntaxKind.UndefinedKeyword &&
        type.kind !== ts.SyntaxKind.NullKeyword &&
        type.kind !== ts.SyntaxKind.NeverKeyword,
      )
      let projected
      if (meaningful.length === 1) {
        projected = safeType(meaningful[0])
      } else if (meaningful.length > 0 && meaningful.every((type) =>
        ts.isLiteralTypeNode(type) && ts.isStringLiteral(type.literal),
      )) {
        projected = factory.createKeywordTypeNode(ts.SyntaxKind.StringKeyword)
      } else {
        projected = unknownType()
      }
      if (!hasNullish || projected.kind === ts.SyntaxKind.UnknownKeyword) return projected
      return factory.createUnionTypeNode([
        projected,
        factory.createKeywordTypeNode(ts.SyntaxKind.UndefinedKeyword),
      ])
    }

    if (ts.isIntersectionTypeNode(node)) {
      const literals = node.types.filter(ts.isTypeLiteralNode)
      if (literals.length === 1) return safeType(literals[0])
      return unknownType()
    }

    if (ts.isTypeReferenceNode(node)) {
      const name = leftmostName(node.typeName)
      const isKnown = builtinTypes.has(name) || pluginDeclaredNames.has(name) || typeParameterNames.has(name)
      if (isExternalReference(node.typeName) || utilityTypes.has(name) || !isKnown) return unknownType()
      const argumentsList = (name === "Hooks" || name === "ModelHooks")
        ? node.typeArguments?.slice(0, 1).map(safeType)
        : node.typeArguments?.map(safeType)
      return factory.updateTypeReferenceNode(node, node.typeName, argumentsList)
    }

    if (ts.isExpressionWithTypeArguments(node)) {
      const expressionName = ts.isIdentifier(node.expression)
        ? node.expression.text
        : ts.isPropertyAccessExpression(node.expression)
          ? node.expression.expression.getText(sourceFile)
          : ""
      if (externalImports.has(expressionName) || utilityTypes.has(expressionName)) return undefined
      return factory.updateExpressionWithTypeArguments(node, node.typeArguments?.map(safeType), node.expression)
    }

    if (ts.isFunctionTypeNode(node)) {
      return factory.updateFunctionTypeNode(
        node,
        node.typeParameters?.map(safeTypeParameter),
        node.parameters.map(safeParameter),
        safeType(node.type),
      )
    }

    if (ts.isConstructorTypeNode(node)) {
      return unknownType()
    }

    if (ts.isArrayTypeNode(node)) return factory.updateArrayTypeNode(node, safeType(node.elementType))
    if (ts.isTupleTypeNode(node)) return unknownType()
    if (ts.isParenthesizedTypeNode(node)) return factory.updateParenthesizedType(node, safeType(node.type))
    if (ts.isTypeOperatorNode(node)) {
      if (node.operator === ts.SyntaxKind.KeyOfKeyword) return factory.createKeywordTypeNode(ts.SyntaxKind.StringKeyword)
      if (node.operator === ts.SyntaxKind.ReadonlyKeyword) return safeType(node.type)
      return unknownType()
    }
    if (ts.isOptionalTypeNode(node)) return factory.updateOptionalTypeNode(node, safeType(node.type))
    if (ts.isRestTypeNode(node)) return factory.updateRestTypeNode(node, safeType(node.type))

    return ts.visitEachChild(node, visitor, transformationContext)
  }

  function safeTypeParameter(node) {
    return factory.updateTypeParameterDeclaration(
      node,
      node.modifiers,
      node.name,
      undefined,
      undefined,
    )
  }

  function safeParameter(node) {
    return factory.updateParameterDeclaration(
      node,
      node.modifiers,
      node.dotDotDotToken,
      node.name,
      node.questionToken,
      safeType(node.type),
      undefined,
    )
  }

  function simplifiedHooksAlias(node, modelHooks) {
    const spec = factory.createTypeParameterDeclaration(undefined, "Spec")
    const callback = factory.createFunctionTypeNode(
      undefined,
      [factory.createParameterDeclaration(undefined, undefined, "input", undefined, unknownType())],
      unknownType(),
    )
    const parameters = [
      factory.createParameterDeclaration(undefined, undefined, "name", undefined, factory.createKeywordTypeNode(ts.SyntaxKind.StringKeyword)),
      factory.createParameterDeclaration(undefined, undefined, "callback", undefined, callback),
    ]
    if (modelHooks) {
      parameters.push(factory.createParameterDeclaration(
        undefined,
        undefined,
        "options",
        factory.createToken(ts.SyntaxKind.QuestionToken),
        unknownType(),
      ))
    }
    const returnType = fileName.includes(`${path.sep}promise${path.sep}`)
      ? factory.createTypeReferenceNode("Promise", [factory.createTypeReferenceNode("Registration")])
      : unknownType()
    return factory.updateTypeAliasDeclaration(
      node,
      node.modifiers,
      node.name,
      [spec],
      factory.createFunctionTypeNode(undefined, parameters, returnType),
    )
  }

  function flattenProperties(node) {
    const type = checker.getTypeAtLocation(node)
    const existing = new Set(node.members?.map(memberNameText).filter(Boolean) ?? [])
    const additions = []
    for (const symbol of checker.getPropertiesOfType(type)) {
      const name = symbol.getName()
      if (existing.has(name) || name.startsWith("__@")) continue
      const location = symbol.valueDeclaration ?? symbol.declarations?.[0] ?? node
      const propertyType = checker.getTypeOfSymbolAtLocation(symbol, location)
      const typeNode = checker.typeToTypeNode(
        propertyType,
        node,
        ts.NodeBuilderFlags.NoTruncation | ts.NodeBuilderFlags.UseAliasDefinedOutsideCurrentScope,
      )
      additions.push(factory.createPropertySignature(
        undefined,
        factory.createStringLiteral(name),
        symbol.flags & ts.SymbolFlags.Optional ? factory.createToken(ts.SyntaxKind.QuestionToken) : undefined,
        safeType(typeNode),
      ))
    }
    return additions
  }

  function visitor(node) {
    if (ts.isImportDeclaration(node)) {
      if (ts.isStringLiteral(node.moduleSpecifier) && !node.moduleSpecifier.text.startsWith(".")) return undefined
      return node
    }
    if (ts.isExportDeclaration(node)) return undefined

    if (ts.isFunctionDeclaration(node) && node.name?.text === "prepareSource") {
      const suffix = fileName.endsWith("source.bun.d.ts")
        ? "Bun"
        : fileName.endsWith("source.node.d.ts")
          ? "Node"
          : ""
      if (suffix) {
        return factory.updateFunctionDeclaration(
          node,
          node.modifiers,
          node.asteriskToken,
          factory.createIdentifier(`prepareSource${suffix}`),
          node.typeParameters?.map(safeTypeParameter),
          node.parameters.map(safeParameter),
          safeType(node.type),
          undefined,
        )
      }
    }

    if (ts.isInterfaceDeclaration(node)) {
      const shouldFlatten = Boolean(node.heritageClauses?.length)
      const members = [
        ...node.members.map((member) => ts.visitEachChild(member, visitor, transformationContext)),
        ...(shouldFlatten ? flattenProperties(node) : []),
      ]
      return factory.updateInterfaceDeclaration(
        node,
        node.modifiers,
        node.name,
        node.typeParameters?.map(safeTypeParameter),
        undefined,
        members,
      )
    }

    if (ts.isTypeAliasDeclaration(node) && node.name.text === "Hooks") {
      return simplifiedHooksAlias(node, false)
    }
    if (ts.isTypeAliasDeclaration(node) && node.name.text === "ModelHooks") {
      return simplifiedHooksAlias(node, true)
    }
    if (ts.isTypeAliasDeclaration(node) && node.name.text === "SlotClaim") {
      return factory.updateTypeAliasDeclaration(
        node,
        node.modifiers,
        node.name,
        undefined,
        unknownType(),
      )
    }

    if (ts.isTypeAliasDeclaration(node) && ts.isIntersectionTypeNode(node.type)) {
      const properties = flattenProperties(node)
      if (properties.length > 0) {
        return factory.createInterfaceDeclaration(
          node.modifiers,
          node.name,
          node.typeParameters?.map(safeTypeParameter),
          undefined,
          properties,
        )
      }
    }

    if (ts.isTypeAliasDeclaration(node)) {
      return factory.updateTypeAliasDeclaration(
        node,
        node.modifiers,
        node.name,
        node.typeParameters?.map(safeTypeParameter),
        safeType(node.type),
      )
    }

    if (ts.isTypeNode(node)) return safeType(node)
    return ts.visitEachChild(node, visitor, transformationContext)
  }

  const transformed = ts.transform(sourceFile, [
    (context) => {
      transformationContext = context
      return (rootNode) => ts.visitNode(rootNode, visitor)
    },
  ])
  const outputFile = path.join(outputRoot, path.relative(packageRoot, fileName))
  fs.mkdirSync(path.dirname(outputFile), { recursive: true })
  fs.writeFileSync(outputFile, printer.printFile(transformed.transformed[0]))
  transformed.dispose()
}

console.log(`SANITIZED_DECLARATIONS_OK files=${inputFiles.length}`)
