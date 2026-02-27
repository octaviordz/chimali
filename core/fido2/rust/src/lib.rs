mod ctap_wrapper;

use ctap_wrapper::CtapWrapper;
use std::sync::Mutex;
use once_cell::sync::Lazy;

static WRAPPER: Lazy<Mutex<CtapWrapper>> = Lazy::new(|| Mutex::new(CtapWrapper::new()));

uniffi::setup_scaffolding!();

#[uniffi::export]
pub fn get_fido_version() -> String {
    "CTAP 2.0".to_string()
}

#[derive(uniffi::Record)]
pub struct CtapResponse {
    pub status: u8,
    pub data: Vec<u8>,
}

#[uniffi::export]
pub fn process_ctap_packet(packet: Vec<u8>) -> CtapResponse {
    let mut wrapper = WRAPPER.lock().unwrap();
    wrapper.process_packet(packet)
}
