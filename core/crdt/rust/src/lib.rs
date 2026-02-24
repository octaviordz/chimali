uniffi::setup_scaffolding!();

use loro::LoroDoc;

#[derive(Debug, thiserror::Error)]
pub enum CrdtError {
    #[error("Failed to import state: {0}")]
    ImportError(String),
}

#[uniffi::export]
pub fn merge_crdt_states(local_state: &[u8], remote_state: &[u8]) -> Result<Vec<u8>, CrdtError> {
    let doc = LoroDoc::new();
    
    if !local_state.is_empty() {
        doc.import(local_state)
            .map_err(|e| CrdtError::ImportError(e.to_string()))?;
    }
    
    if !remote_state.is_empty() {
        doc.import(remote_state)
            .map_err(|e| CrdtError::ImportError(e.to_string()))?;
    }
    
    Ok(doc.export_snapshot())
}
