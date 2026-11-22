// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "MerchantNativePolicies", platforms: [.macOS(.v12), .iOS(.v15)],
    products: [.library(name: "MerchantNativePolicies", targets: ["MerchantNativePolicies"])],
    targets: [
        .target(name: "MerchantNativePolicies", path: "App/App", exclude: [
            "AppDelegate.swift", "SceneDelegate.swift", "SecureSessionPlugin.swift", "IncidentFileDownloadPlugin.swift",
            "MerchantBridgeViewController.swift", "Assets.xcassets", "Base.lproj", "Info.plist", "App.entitlements",
            "PrivacyInfo.xcprivacy", "capacitor.config.json", "config.xml", "public"],
            sources: ["MerchantOriginPolicy.swift", "IncidentExportPolicy.swift"]),
        .testTarget(name: "MerchantNativePoliciesTests", dependencies: ["MerchantNativePolicies"], path: "PolicyTests")
    ])
