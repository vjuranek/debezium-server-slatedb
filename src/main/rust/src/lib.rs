use slatedb::Db;
use slatedb::object_store::{ObjectStore, memory::InMemory};
use std::sync::Arc;
use std::sync::Mutex;

static KV_STORE: Mutex<Option<Db>> = Mutex::new(None);

#[unsafe(no_mangle)]
pub async extern "C" fn init(db_dir: &str) {
    let object_store: Arc<dyn ObjectStore> = Arc::new(InMemory::new());
    let _ = *KV_STORE.lock().unwrap() = Some(Db::open(db_dir, object_store).await.expect("Failed to initialize the DB"));
    println!{"DB inialized"}
}

#[unsafe(no_mangle)]
pub async extern "C" fn insert(key: &str, value: &str) {
    let mut db = KV_STORE.lock().unwrap();
    let _ = (*db).as_mut().unwrap().put(key.as_bytes(), value.as_bytes()).await;
    println!("inserted {} -> {}", key, value);
}

// #[unsafe(no_mangle)]
// pub async extern "C" fn get(key: &str) {
//     let mut db = KV_STORE.lock().unwrap();
//     let Ok(Some(value)) = (*db).as_mut().unwrap().get(key.as_bytes()).await else {panic!("failed to get {}", key)};
//     println!("get {} -> {}", key, std::str::from_utf8(&value).unwrap());
//     value;
// }

#[unsafe(no_mangle)]
pub async extern "C" fn close() {
    let mut db = KV_STORE.lock().unwrap();
    let _ = (*db).as_mut().unwrap().close().await;
    println!("DB closed");
}

