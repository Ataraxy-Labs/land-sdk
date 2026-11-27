//! Runs the SDK against the fake `land` in fake, named as the executable.
use std::path::PathBuf;
use useland::{AddOptions, Kind, Land};

fn fake(name: &str) -> (Land, PathBuf) {
    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    let log = std::env::temp_dir().join(format!("useland-rs-{}-{name}", std::process::id()));
    let mut land = Land::new().env("FAKE_LAND_LOG", log.to_str().unwrap());
    land.bin = Some(root.join("../fake/land"));
    (land, log)
}
fn argv(log: &PathBuf) -> String {
    std::fs::read_to_string(log).unwrap().trim().to_owned()
}

#[test]
fn merge_returns_the_answer_and_passes_json() {
    let (land, log) = fake("merge");
    let a = land.merge(&["sub/ok"], "main", false).unwrap();
    assert_eq!(a["landed"][0], "sub/ok");
    assert_eq!(argv(&log), "merge sub/ok --onto main --json");
}

#[test]
fn exit_1_is_refused_and_keeps_the_answer() {
    let (land, _) = fake("refused");
    let e = land.merge(&["sub/bad"], "main", false).unwrap_err();
    assert_eq!((e.kind, e.code), (Kind::Refused, 1));
    assert_eq!(e.answer["refused"].as_array().unwrap().len(), 1);
}

#[test]
fn exit_2_carries_fix_and_docs() {
    let (land, _) = fake("usage");
    let e = land.logs(Some("users"), None).unwrap_err();
    assert_eq!(e.kind, Kind::Usage);
    assert_eq!(e.fix.as_deref(), Some("land merge <branch>... --onto main"));
    assert_eq!(e.docs.as_deref(), Some("https://docs.useland.dev/errors#exit-2"));
}

#[test]
fn exit_3_is_still_running() {
    let (land, _) = fake("running");
    let e = land.add(&AddOptions::default()).unwrap_err();
    assert_eq!(e.kind, Kind::StillRunning);
    assert_eq!(e.fix.as_deref(), Some("land add"));
}

#[test]
fn a_local_run_is_never_signed() {
    let (land, _) = fake("local");
    let a = land.local::<&str>(&[]).unwrap();
    assert_eq!(a["signed"], false);
    assert_eq!(a["tests"]["ran_units"][0]["name"], "spec");
}

#[test]
fn a_secrets_value_goes_on_stdin() {
    let (land, log) = fake("secret");
    land.secrets().set("STRIPE_KEY", "sk_test_1").unwrap();
    assert_eq!(argv(&log), "secret set STRIPE_KEY --json");
    assert_eq!(std::fs::read_to_string(format!("{}.stdin", log.display())).unwrap(), "sk_test_1");
}

#[test]
fn add_and_logs_switches() {
    let (land, log) = fake("switches");
    let o = AddOptions { dry_run: true, test: vec!["pytest -q".into()], allow_test_edits: Some(false), cpus: Some(4), ..Default::default() };
    assert_eq!(land.add(&o).unwrap_err().kind, Kind::StillRunning);
    assert_eq!(argv(&log), "add --dry-run --test pytest -q --no-allow-test-edits --cpus 4 --json");
    assert_eq!(land.logs(Some("users"), Some("ls")).unwrap_err().kind, Kind::Usage);
    assert_eq!(argv(&log), "logs users --run ls --json");
}
