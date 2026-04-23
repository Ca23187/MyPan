import { ElMessageBox } from 'element-plus'

const confirm = (message, okfun) => {
    ElMessageBox.confirm(message, 'Confirmation', {
        confirmButtonText: 'Confirm',
        cancelButtonText: 'Cancel',
        type: 'info',
    }).then(() => {
        okfun()
    }).catch(() => {})
}

export default confirm;