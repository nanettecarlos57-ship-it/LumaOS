using UnityEngine;

/// <summary>
/// Creates the first NATION FC training-match prototype at runtime.
/// Attach this component to an empty GameObject in a Unity scene.
/// </summary>
public class NationFCBootstrap : MonoBehaviour
{
    [Header("Pitch")]
    public float pitchLength = 90f;
    public float pitchWidth = 58f;

    private Material grass;
    private Material line;
    private Material blue;
    private Material white;
    private Material dark;
    private Material ballMat;

    private NationFCPlayerController playerController;
    private Rigidbody ballBody;
    private int homeScore;
    private int awayScore;
    private float messageUntil;
    private string matchMessage = "KICK OFF";

    private void Start()
    {
        Application.targetFrameRate = 60;
        Screen.orientation = ScreenOrientation.LandscapeLeft;
        CreateMaterials();
        CreatePitch();
        CreateGoals();
        CreateBall();
        CreatePlayers();
        CreateCamera();
        CreateLighting();
    }

    private void CreateMaterials()
    {
        grass = MakeMaterial(new Color(0.08f, 0.36f, 0.18f));
        line = MakeMaterial(Color.white);
        blue = MakeMaterial(new Color(0.05f, 0.28f, 0.85f));
        white = MakeMaterial(new Color(0.92f, 0.94f, 0.98f));
        dark = MakeMaterial(new Color(0.035f, 0.055f, 0.09f));
        ballMat = MakeMaterial(Color.white);
    }

    private Material MakeMaterial(Color color)
    {
        Shader shader = Shader.Find("Universal Render Pipeline/Lit");
        if (shader == null) shader = Shader.Find("Standard");
        Material material = new Material(shader);
        material.color = color;
        return material;
    }

    private void CreatePitch()
    {
        GameObject ground = GameObject.CreatePrimitive(PrimitiveType.Plane);
        ground.name = "Pitch";
        ground.transform.position = Vector3.zero;
        ground.transform.localScale = new Vector3(pitchLength / 10f, 1f, pitchWidth / 10f);
        ground.GetComponent<Renderer>().material = grass;

        float halfL = pitchLength / 2f;
        float halfW = pitchWidth / 2f;
        MakeLine("Touchline North", new Vector3(0, 0.035f, halfW), new Vector3(pitchLength, 0.04f, 0.12f));
        MakeLine("Touchline South", new Vector3(0, 0.035f, -halfW), new Vector3(pitchLength, 0.04f, 0.12f));
        MakeLine("Goal Line West", new Vector3(-halfL, 0.035f, 0), new Vector3(0.12f, 0.04f, pitchWidth));
        MakeLine("Goal Line East", new Vector3(halfL, 0.035f, 0), new Vector3(0.12f, 0.04f, pitchWidth));
        MakeLine("Halfway Line", Vector3.up * 0.04f, new Vector3(0.12f, 0.04f, pitchWidth));
        CreateCircle(Vector3.zero, 9.15f, line, false);
        CreatePenaltyArea(-halfL, halfW, halfW, false);
        CreatePenaltyArea(halfL, halfW, halfW, true);

        // Simple dark-blue stands make the field read as a stadium.
        for (int side = -1; side <= 1; side += 2)
        {
            GameObject stand = GameObject.CreatePrimitive(PrimitiveType.Cube);
            stand.name = side < 0 ? "North Stand" : "South Stand";
            stand.transform.position = new Vector3(0, 2.2f, side * (halfW + 7f));
            stand.transform.localScale = new Vector3(pitchLength + 20f, 4.4f, 5f);
            stand.GetComponent<Renderer>().material = dark;
        }
    }

    private void MakeLine(string name, Vector3 position, Vector3 scale)
    {
        GameObject obj = GameObject.CreatePrimitive(PrimitiveType.Cube);
        obj.name = name;
        obj.transform.position = position;
        obj.transform.localScale = scale;
        obj.GetComponent<Renderer>().material = line;
    }

    private void CreateCircle(Vector3 center, float radius, Material material, bool filled)
    {
        int segments = 64;
        for (int i = 0; i < segments; i++)
        {
            float a = i * Mathf.PI * 2f / segments;
            float b = (i + 1) * Mathf.PI * 2f / segments;
            Vector3 p1 = center + new Vector3(Mathf.Cos(a) * radius, 0.045f, Mathf.Sin(a) * radius);
            Vector3 p2 = center + new Vector3(Mathf.Cos(b) * radius, 0.045f, Mathf.Sin(b) * radius);
            Vector3 delta = p2 - p1;
            GameObject segment = GameObject.CreatePrimitive(PrimitiveType.Cube);
            segment.name = "Centre Circle Marking";
            segment.transform.position = (p1 + p2) * 0.5f;
            segment.transform.rotation = Quaternion.LookRotation(delta);
            segment.transform.localScale = new Vector3(0.11f, 0.035f, delta.magnitude);
            segment.GetComponent<Renderer>().material = material;
        }
    }

    private void CreatePenaltyArea(float x, float halfW, float ignored, bool right)
    {
        float direction = right ? -1f : 1f;
        float edgeX = x + direction * 16.5f;
        float z = 20.15f;
        MakeLine("Penalty Box Front", new Vector3(edgeX, 0.045f, 0), new Vector3(0.11f, 0.04f, z * 2));
        MakeLine("Penalty Box Upper", new Vector3(x + direction * 8.25f, 0.045f, z), new Vector3(16.5f, 0.04f, 0.11f));
        MakeLine("Penalty Box Lower", new Vector3(x + direction * 8.25f, 0.045f, -z), new Vector3(16.5f, 0.04f, 0.11f));
    }

    private void CreateGoals()
    {
        CreateGoal(-pitchLength / 2f, false);
        CreateGoal(pitchLength / 2f, true);
    }

    private void CreateGoal(float x, bool right)
    {
        float z = 3.66f;
        float depthDirection = right ? 1f : -1f;
        MakePost(new Vector3(x, 1.2f, -z));
        MakePost(new Vector3(x, 1.2f, z));
        GameObject crossbar = GameObject.CreatePrimitive(PrimitiveType.Cube);
        crossbar.name = "Goal Crossbar";
        crossbar.transform.position = new Vector3(x, 2.4f, 0);
        crossbar.transform.localScale = new Vector3(0.16f, 0.16f, z * 2f);
        crossbar.GetComponent<Renderer>().material = line;

        GameObject trigger = new GameObject(right ? "Home Goal Trigger" : "Away Goal Trigger");
        trigger.transform.position = new Vector3(x + depthDirection * 1.2f, 1.2f, 0);
        BoxCollider collider = trigger.AddComponent<BoxCollider>();
        collider.isTrigger = true;
        collider.size = new Vector3(2.2f, 2.5f, 7.4f);
        NationFCGoal goal = trigger.AddComponent<NationFCGoal>();
        goal.bootstrap = this;
        goal.homeGoal = !right;
    }

    private void MakePost(Vector3 position)
    {
        GameObject post = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
        post.name = "Goal Post";
        post.transform.position = position;
        post.transform.localScale = new Vector3(0.08f, 1.2f, 0.08f);
        post.GetComponent<Renderer>().material = line;
    }

    private void CreateBall()
    {
        GameObject ball = GameObject.CreatePrimitive(PrimitiveType.Sphere);
        ball.name = "Match Ball";
        ball.transform.position = new Vector3(0, 0.65f, 0);
        ball.transform.localScale = Vector3.one * 0.72f;
        ball.GetComponent<Renderer>().material = ballMat;
        ballBody = ball.AddComponent<Rigidbody>();
        ballBody.mass = 0.43f;
        ballBody.linearDamping = 0.18f;
        ballBody.angularDamping = 0.08f;
        ballBody.collisionDetectionMode = CollisionDetectionMode.ContinuousDynamic;
        ball.AddComponent<NationFCBall>();
    }

    private void CreatePlayers()
    {
        GameObject player = CreateFootballer("NATION FC Player", new Vector3(-8, 1, 0), blue);
        playerController = player.AddComponent<NationFCPlayerController>();
        playerController.ball = ballBody;

        CreateFootballer("Opponent 1", new Vector3(13, 1, 5), white).AddComponent<NationFCOpponent>().ball = ballBody;
        CreateFootballer("Opponent 2", new Vector3(22, 1, -10), white).AddComponent<NationFCOpponent>().ball = ballBody;
        CreateFootballer("Opponent 3", new Vector3(5, 1, -18), white).AddComponent<NationFCOpponent>().ball = ballBody;
    }

    private GameObject CreateFootballer(string name, Vector3 position, Material kit)
    {
        GameObject player = GameObject.CreatePrimitive(PrimitiveType.Capsule);
        player.name = name;
        player.transform.position = position;
        player.transform.localScale = new Vector3(0.85f, 1f, 0.85f);
        player.GetComponent<Renderer>().material = kit;
        player.AddComponent<Rigidbody>().isKinematic = true;
        return player;
    }

    private void CreateCamera()
    {
        Camera camera = Camera.main;
        if (camera == null)
        {
            GameObject cameraObject = new GameObject("Main Camera");
            camera = cameraObject.AddComponent<Camera>();
            camera.tag = "MainCamera";
        }
        camera.transform.position = new Vector3(-13, 18, -20);
        camera.transform.rotation = Quaternion.Euler(35, 32, 0);
        NationFCCameraFollow follow = camera.gameObject.AddComponent<NationFCCameraFollow>();
        follow.target = playerController.transform;
    }

    private void CreateLighting()
    {
        GameObject lightObject = new GameObject("Stadium Sun");
        Light sun = lightObject.AddComponent<Light>();
        sun.type = LightType.Directional;
        sun.intensity = 1.25f;
        lightObject.transform.rotation = Quaternion.Euler(50, -30, 0);
        RenderSettings.ambientLight = new Color(0.55f, 0.58f, 0.65f);
    }

    public void GoalScored(bool homeGoal)
    {
        if (homeGoal) homeScore++; else awayScore++;
        matchMessage = homeGoal ? "NATION FC SCORES!" : "OPPONENT SCORES!";
        messageUntil = Time.time + 2.5f;
        Invoke(nameof(ResetKickoff), 2.5f);
    }

    private void ResetKickoff()
    {
        if (ballBody == null) return;
        ballBody.linearVelocity = Vector3.zero;
        ballBody.angularVelocity = Vector3.zero;
        ballBody.position = new Vector3(0, 0.65f, 0);
        matchMessage = "PLAY ON";
        messageUntil = Time.time + 1.5f;
    }

    private void OnGUI()
    {
        GUI.skin.label.fontSize = Mathf.Max(18, Screen.height / 28);
        GUI.skin.button.fontSize = Mathf.Max(16, Screen.height / 34);
        GUI.color = new Color(1, 1, 1, 0.96f);
        GUI.Box(new Rect(Screen.width * 0.37f, 12, Screen.width * 0.26f, 48), "NATION FC   " + homeScore + "  -  " + awayScore);
        if (Time.time < messageUntil)
        {
            GUI.Box(new Rect(Screen.width * 0.34f, 66, Screen.width * 0.32f, 42), matchMessage);
        }

        float button = Mathf.Clamp(Screen.height * 0.13f, 54, 92);
        float margin = 18;
        float left = margin;
        float bottom = Screen.height - button - margin;
        if (GUI.RepeatButton(new Rect(left + button, bottom - button, button, button), "UP")) NationFCPlayerController.moveForward = true;
        if (GUI.RepeatButton(new Rect(left + button, bottom + button, button, button), "DOWN")) NationFCPlayerController.moveBack = true;
        if (GUI.RepeatButton(new Rect(left, bottom, button, button), "LEFT")) NationFCPlayerController.moveLeft = true;
        if (GUI.RepeatButton(new Rect(left + button * 2, bottom, button, button), "RIGHT")) NationFCPlayerController.moveRight = true;

        float right = Screen.width - margin - button;
        if (GUI.Button(new Rect(right, bottom, button, button), "PASS")) playerController.TryPass();
        if (GUI.Button(new Rect(right, bottom - button - 10, button, button), "SHOOT")) playerController.TryShoot();
        if (GUI.RepeatButton(new Rect(right - button - 10, bottom, button, button), "SPRINT")) NationFCPlayerController.sprinting = true;

        // Reset transient button flags after all controls have been evaluated.
        NationFCPlayerController.moveForward = false;
        NationFCPlayerController.moveBack = false;
        NationFCPlayerController.moveLeft = false;
        NationFCPlayerController.moveRight = false;
        NationFCPlayerController.sprinting = false;
    }
}

public class NationFCGoal : MonoBehaviour
{
    public NationFCBootstrap bootstrap;
    public bool homeGoal;

    private void OnTriggerEnter(Collider other)
    {
        if (other.GetComponent<NationFCBall>() != null && bootstrap != null)
            bootstrap.GoalScored(homeGoal);
    }
}

public class NationFCBall : MonoBehaviour { }

public class NationFCPlayerController : MonoBehaviour
{
    public Rigidbody ball;
    public static bool moveForward, moveBack, moveLeft, moveRight, sprinting;
    private CharacterController controller;
    private float verticalVelocity;
    private float nextKickTime;

    private void Awake()
    {
        controller = gameObject.AddComponent<CharacterController>();
        controller.height = 2f;
        controller.radius = 0.42f;
        controller.center = Vector3.zero;
    }

    private void Update()
    {
        float x = (moveRight ? 1 : 0) - (moveLeft ? 1 : 0);
        float z = (moveForward ? 1 : 0) - (moveBack ? 1 : 0);
        x += Input.GetAxisRaw("Horizontal");
        z += Input.GetAxisRaw("Vertical");
        Vector3 direction = new Vector3(x, 0, z).normalized;
        float speed = sprinting || Input.GetKey(KeyCode.LeftShift) ? 9f : 5.5f;

        if (direction.sqrMagnitude > 0.01f)
        {
            transform.forward = Vector3.Slerp(transform.forward, direction, 12f * Time.deltaTime);
        }

        if (controller.isGrounded) verticalVelocity = -1f;
        else verticalVelocity -= 20f * Time.deltaTime;
        Vector3 movement = direction * speed;
        movement.y = verticalVelocity;
        controller.Move(movement * Time.deltaTime);
    }

    public void TryPass() { Kick(8f, 0.35f); }
    public void TryShoot() { Kick(18f, 0.7f); }

    private void Kick(float force, float lift)
    {
        if (ball == null || Time.time < nextKickTime) return;
        Vector3 offset = ball.position - transform.position;
        if (offset.magnitude > 4f) return;
        Vector3 direction = transform.forward + Vector3.up * lift;
        ball.AddForce(direction.normalized * force, ForceMode.Impulse);
        nextKickTime = Time.time + 0.35f;
    }
}

public class NationFCOpponent : MonoBehaviour
{
    public Rigidbody ball;
    private void Update()
    {
        if (ball == null) return;
        Vector3 target = ball.position - transform.position;
        target.y = 0;
        if (target.magnitude > 1.8f)
        {
            Vector3 step = target.normalized * 3.2f * Time.deltaTime;
            transform.position += step;
            if (target.sqrMagnitude > 0.01f) transform.forward = target.normalized;
        }
    }
}

public class NationFCCameraFollow : MonoBehaviour
{
    public Transform target;
    private Vector3 offset = new Vector3(-13, 18, -20);

    private void LateUpdate()
    {
        if (target == null) return;
        Vector3 desired = target.position + offset;
        transform.position = Vector3.Lerp(transform.position, desired, 4f * Time.deltaTime);
        transform.LookAt(target.position + Vector3.up * 0.5f);
    }
}
