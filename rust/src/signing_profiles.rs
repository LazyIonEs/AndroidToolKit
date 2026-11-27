use crate::ToolKitRustError;

pub fn signing_profiles_key(key_if_missing: Option<Vec<u8>>) -> Result<Vec<u8>, ToolKitRustError> {
    let entry = keyring::Entry::new("org.tool.kit.apk-signing", "signing-profiles-key")
        .map_err(|_| credential_store_error())?;
    match (entry.get_secret(), key_if_missing) {
        (Ok(key), _) => Ok(key),
        (Err(keyring::Error::NoEntry), Some(key)) => {
            entry
                .set_secret(&key)
                .map_err(|_| credential_store_error())?;
            Ok(key)
        }
        _ => Err(credential_store_error()),
    }
}

fn credential_store_error() -> ToolKitRustError {
    ToolKitRustError::Error("Unable to access the signing profile key".into())
}
