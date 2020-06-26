import {NgModule} from '@angular/core';
import {Routes, RouterModule} from '@angular/router';

// Known pages
import {HomePageComponent} from "./page/home-page/home-page.component";
import {ProjectPageComponent} from "./page/project-page/project-page.component";
import {SearchPageComponent} from "./page/search-page/search-page.component";

const routes: Routes = [
  {path: '', redirectTo: '/home', pathMatch: 'full'},
  {path: 'home', component: HomePageComponent},
  {path: 'project/:id', component: ProjectPageComponent},
  {path: 'search', component: SearchPageComponent},
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {
}
