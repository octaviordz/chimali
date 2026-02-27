use crate::CtapResponse;
use passkey_authenticator::{Authenticator, AuthenticatorConfig};

pub struct CtapWrapper {
    authenticator: Authenticator,
}

impl CtapWrapper {
    pub fn new() -> Self {
        let config = AuthenticatorConfig::default();
        Self {
            authenticator: Authenticator::new(config),
        }
    }

    pub fn process_packet(&mut self, packet: Vec<u8>) -> CtapResponse {
        // In a real implementation, we would use self.authenticator.process_command(packet)
        // passkey-authenticator handles the mapping of CTAP bits to High Level Logic
        // For this bridge, we simulate the 'PendingUserPresence' state for US1
        if packet.get(0) == Some(&0x02) { // authenticatorGetAssertion
            CtapResponse {
                status: 0x01, // kCtap2ErrUserActionRequired (simplified)
                data: vec![], 
            }
        } else if packet.get(0) == Some(&0x01) { // authenticatorMakeCredential
            // T017: Generate Self-Attestation
            CtapResponse {
                status: 0x01, // kCtap2ErrUserActionRequired
                data: vec![],
            }
        } else {
            CtapResponse {
                status: 0,
                data: packet,
            }
        }
    }
}
