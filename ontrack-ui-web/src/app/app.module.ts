import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { GraphQLModule } from './graphql.module';
import { HttpClientModule } from '@angular/common/http';

import {ToolbarComponent} from "./core/toolbar/toolbar.component";
import {SearchBoxComponent} from "./core/search-box/search-box.component";
import {UserMenuComponent} from "./core/user-menu/user-menu.component";

import {HomePageComponent} from "./page/home-page/home-page.component";
import {ProjectPageComponent} from "./page/project-page/project-page.component";
import {ReactiveFormsModule} from "@angular/forms";

@NgModule({
  declarations: [
    AppComponent,

    ToolbarComponent,
    UserMenuComponent,
    SearchBoxComponent,

    HomePageComponent,
    ProjectPageComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    NgbModule,
    GraphQLModule,
    HttpClientModule,
    ReactiveFormsModule
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
