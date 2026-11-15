#if UNITY_EDITOR
using System.IO;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

/// <summary>Editor helpers for creating and packaging the NATION FC starter build.</summary>
public static class NationFCProjectTools
{
    private const string ScenePath = "Assets/Scenes/NationFC.unity";

    [MenuItem("NATION FC/Create Starter Scene")]
    public static void CreateStarterScene()
    {
        Directory.CreateDirectory("Assets/Scenes");
        Scene scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
        GameObject root = new GameObject("NATION FC Game");
        root.AddComponent<NationFCBootstrap>();
        EditorSceneManager.SaveScene(scene, ScenePath);
        EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };
        PlayerSettings.productName = "NATION FC";
        PlayerSettings.companyName = "Nation HuB Tech";
        PlayerSettings.defaultInterfaceOrientation = UIOrientation.LandscapeLeft;
        Debug.Log("NATION FC starter scene created. Press Play to run the prototype.");
    }

    [MenuItem("NATION FC/Build Android APK")]
    public static void BuildAndroidApk()
    {
        if (!BuildPipeline.IsBuildTargetSupported(BuildTargetGroup.Android, BuildTarget.Android))
        {
            Debug.LogError("Android Build Support is not installed. Add Android Build Support, SDK & NDK Tools, and OpenJDK in Unity Hub.");
            return;
        }

        Directory.CreateDirectory("Builds");
        PlayerSettings.productName = "NATION FC";
        PlayerSettings.companyName = "Nation HuB Tech";
        PlayerSettings.defaultInterfaceOrientation = UIOrientation.LandscapeLeft;
        EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.Android, BuildTarget.Android);

        BuildPlayerOptions options = new BuildPlayerOptions
        {
            scenes = new[] { ScenePath },
            locationPathName = "Builds/NATION-FC.apk",
            target = BuildTarget.Android,
            options = BuildOptions.None
        };
        BuildReport report = BuildPipeline.BuildPlayer(options);
        if (report.summary.result == BuildResult.Succeeded)
            Debug.Log("NATION FC APK created: Builds/NATION-FC.apk");
        else
            Debug.LogError("NATION FC Android build did not succeed. Check the Console for errors.");
    }
}
#endif
