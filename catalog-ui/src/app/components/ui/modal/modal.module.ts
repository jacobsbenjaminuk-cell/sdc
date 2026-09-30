import { NgModule } from "@angular/core";
import { CommonModule } from '@angular/common';
import { AngularDraggableModule } from 'angular2-draggable';
import { ModalService } from 'app/services/modal.service';
import {ModalComponent} from "./modal.component";

@NgModule({
    declarations: [
        ModalComponent
    ],
    imports: [CommonModule, AngularDraggableModule],
    exports: [ModalComponent],
    entryComponents: [ //need to add anything that will be dynamically created
        ModalComponent
    ],
    providers: [ModalService]
})
export class ModalModule {

}