# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
当前项目转换自Java项目,路径是: D:\bqyHome\dev_code\IdeaProjects-plugins\first-plugin\RestfulToolkitPlus-master
IntelliJ IDEA plugin **QuickRestfulToolkit** — enables jumping from a URL path to the matching REST controller method. Supports Spring MVC / Spring Boot and JAX-RS, written in Kotlin, targeting IntelliJ IDEA 2024.3+.

## Build & Run Commands

```bash
# Build plugin (produces .zip in build/distributions/)
./gradlew buildPlugin

# Run standalone IDE instance with plugin loaded
./gradlew runIde

# Run plugin tests
./gradlew test

# Run a single test class
./gradlew test --tests com.example.MyTestClass

# Publish plugin to marketplace
./gradlew publishPlugin
```

All Gradle commands use the included wrapper (`./gradlew`). JDK 21 is required.

## Architecture

### Entry Point
- `plugin.xml` — declares plugin ID, dependencies (Java, Properties, YAML, Kotlin), action, and project service
- `GotoRequestMappingAction` — main action bound to `Ctrl+Alt+/`, extends `GotoActionBase`
- `GotoRequestMappingConfiguration` — stores user-selected HTTP method filter (Kotlin state persistence via `@State`)

### Core Flow
1. User triggers action → `GotoRequestMappingAction.gotoActionPerformed()`
2. Creates `GotoRequestMappingModel` + `GotoRequestMappingContributor`
3. Model queries resolvers (Spring/JAX-RS) for all REST endpoints in scope
4. `GotoRequestMappingProvider` filters results against user input via `UrlPatternUtils.matches()`
5. Selection navigates to the underlying `PsiMethod` via `RestServiceItem.navigate()`

### Package Structure

| Package | Role |
|---|---|
| `annotations/` | `SpringControllerAnnotation`, `SpringRequestMethodAnnotation`, `JaxrsPathAnnotation`, `JaxrsHttpMethodAnnotation`, `PathMappingAnnotation` — annotation metadata used by resolvers |
| `method/` | `HttpMethod` enum (GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS/TRACE/CONNECT) + `RequestPath` data class |
| `common/spring/` | `RequestMappingAnnotationHelper` — parses `@RequestMapping` values for Spring |
| `common/jaxrs/` | `JaxrsAnnotationHelper` — parses `@Path` values for JAX-RS |
| `common/` | Utilities: `PsiAnnotationHelper`, `UrlPatternUtils`, `ToolkitIcons`; Resolvers: `ServiceResolver` interface, `BaseServiceResolver`, `SpringResolver`, `JaxrsResolver`, `ServiceHelper` |
| `navigation/action/` | `RestServiceItem` (model item), `GotoRequestMappingModel`, `GotoRequestMappingContributor`, `GotoRequestMappingProvider`, `GotoRequestMappingAction`, `GotoRequestMappingConfiguration`, `RestServiceChooseByNamePopup` |

### Resolver Pattern
- `ServiceResolver` interface → two implementations: `SpringResolver` and `JaxrsResolver`
- Both extend `BaseServiceResolver` which handles module vs. project scope
- Uses `AnnotatedElementsSearch` to find classes/methods by annotation type, then parses URI paths from annotation values
- `SpringResolver` combines class-level and method-level `@RequestMapping` paths; `JaxrsResolver` does the same for `@Path` annotations

### URL Matching
- `UrlPatternUtils` normalizes user input (strips host, query, fragment, quotes, backslashes)
- Matches against endpoint paths using exact match, substring match, and regex conversion (supports `{param}` and `{param:regex}` patterns)
- Builds candidate suffix paths so partial input like `/api/us` can match `/api/users/1`

## Key Conventions

- All production source code lives in `src/main/java/` despite being Kotlin files (see `build.gradle.kts` sourceSets config). Files in `src/main/kotlin/` are template scaffolding (`MyToolWindow`, `MyMessageBundle`) not wired into `plugin.xml` — ignore them.
- Java/Kotlin interop is intentional — the plugin searches for Java-style annotation qualified names regardless of whether the target controller is written in Java or Kotlin
- Plugin icon assets are in `src/main/resources/icons/` (PNG files for HTTP methods, service icon)
- No test code currently exists (`src/test/` directory is absent)
