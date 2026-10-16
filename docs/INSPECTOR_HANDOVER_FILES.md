# Inspector handover commit file manifest

Reviewed feature scope: 48 files. This manifest lists the files selected for the local handover commit; no push is performed.

The shared canonical/authentication, build configuration and image dependencies were previously uncommitted and are necessary for a standalone Inspector build. ProductRepository includes only shared canonical adapter compatibility. FarmerShell is staged only for the one-line mapNotNull filename fix; its other existing changes remain unstaged.

- `app/build.gradle.kts`
- `app/src/androidTest/java/com/example/farmapp/inspector/InspectorUiTest.kt`
- `app/src/main/java/com/example/farmapp/data/model/AuthModels.kt`
- `app/src/main/java/com/example/farmapp/data/model/FarmerProductModels.kt`
- `app/src/main/java/com/example/farmapp/data/model/canonical/CanonicalAuthDtos.kt`
- `app/src/main/java/com/example/farmapp/data/model/canonical/CanonicalFarmerModels.kt`
- `app/src/main/java/com/example/farmapp/data/model/canonical/CanonicalProductDtos.kt`
- `app/src/main/java/com/example/farmapp/data/remote/ApiClient.kt`
- `app/src/main/java/com/example/farmapp/data/remote/FarmApiService.kt`
- `app/src/main/java/com/example/farmapp/inspector/InspectorApi.kt`
- `app/src/main/java/com/example/farmapp/inspector/InspectorModels.kt`
- `app/src/main/java/com/example/farmapp/inspector/InspectorRepository.kt`
- `app/src/main/java/com/example/farmapp/inspector/InspectorShell.kt`
- `app/src/main/java/com/example/farmapp/inspector/InspectorViewModel.kt`
- `app/src/main/java/com/example/farmapp/network/AuthTokenInterceptor.kt`
- `app/src/main/java/com/example/farmapp/network/AuthValidation.kt`
- `app/src/main/java/com/example/farmapp/network/ImageRequestInterceptor.kt`
- `app/src/main/java/com/example/farmapp/network/canonical/CanonicalApiResponse.kt`
- `app/src/main/java/com/example/farmapp/network/canonical/CanonicalAuthService.kt`
- `app/src/main/java/com/example/farmapp/network/canonical/CanonicalBackendApi.kt`
- `app/src/main/java/com/example/farmapp/network/canonical/CanonicalFarmService.kt`
- `app/src/main/java/com/example/farmapp/network/canonical/CanonicalFarmerMapper.kt`
- `app/src/main/java/com/example/farmapp/network/canonical/CanonicalJson.kt`
- `app/src/main/java/com/example/farmapp/repository/AuthRepository.kt`
- `app/src/main/java/com/example/farmapp/repository/ProductRepository.kt`
- `app/src/main/java/com/example/farmapp/ui/navigation/AppNavigation.kt`
- `app/src/main/java/com/example/farmapp/ui/navigation/AuthDestination.kt`
- `app/src/main/java/com/example/farmapp/ui/screens/FarmerShell.kt`
- `app/src/main/java/com/example/farmapp/ui/screens/LoginScreen.kt`
- `app/src/main/java/com/example/farmapp/ui/screens/ProductImage.kt`
- `app/src/main/java/com/example/farmapp/ui/screens/auth/SignInScreen.kt`
- `app/src/main/java/com/example/farmapp/viewmodel/AuthViewModel.kt`
- `app/src/test/java/com/example/farmapp/inspector/InspectorModuleTest.kt`
- `app/src/test/java/com/example/farmapp/inspector/InspectorWorkflowTest.kt`
- `app/src/test/java/com/example/farmapp/network/ImageRequestInterceptorTest.kt`
- `app/src/test/java/com/example/farmapp/network/canonical/CanonicalContractTest.kt`
- `app/src/test/java/com/example/farmapp/network/canonical/CanonicalIntegrationAdapterTest.kt`
- `app/src/test/java/com/example/farmapp/network/canonical/DevelopmentBackendTest.kt`
- `app/src/test/resources/canonical/categories.json`
- `app/src/test/resources/canonical/error.json`
- `app/src/test/resources/canonical/login.json`
- `app/src/test/resources/canonical/products.json`
- `app/src/test/resources/canonical/register.json`
- `docs/DANIEL_NGROK_DEVELOPMENT.md`
- `docs/INSPECTOR_HANDOVER_BRIAN.md`
- `docs/INSPECTOR_HANDOVER_FILES.md`
- `docs/INSPECTOR_PHASE_1.md`
- `gradle.properties`

Excluded and preserved: .idea changes, unrelated Farmer UI/draft/publisher edits, CanonicalUploadSafetyTest.kt, the live CanonicalBackendDeviceTest.kt and CANONICAL_LOCAL_DEMO.md. local.properties, .gradle, build outputs, APKs, keystores and credentials are not staged.
