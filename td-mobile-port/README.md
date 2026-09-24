# Tiberian Dawn Android mobile port

Isolated build branch for a touch-first Android build of C&C: Tiberian Dawn.

The workflow checks out the pinned public Android upstream commit
`f80b8aef0877ed0fc6ca7ca5771f4ca217327c6c`, applies the files in this directory,
builds `:app:assembleMobileDebug`, verifies the ARM64 APK signature, and uploads
`tiberian-dawn-mobile-debug` as a GitHub Actions artifact.

This branch is isolated from the repository's main branch.
